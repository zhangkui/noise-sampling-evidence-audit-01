package com.citynoise.evidence.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.citynoise.evidence.audit.AuditChainService;
import com.citynoise.evidence.audit.AuditConstants;
import com.citynoise.evidence.common.BusinessException;
import com.citynoise.evidence.common.ErrorCode;
import com.citynoise.evidence.common.RedisDistributedLock;
import com.citynoise.evidence.common.WebUtils;
import com.citynoise.evidence.dto.BatchCreateRequest;
import com.citynoise.evidence.entity.SamplingBatch;
import com.citynoise.evidence.mapper.SamplingBatchMapper;
import com.citynoise.evidence.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BatchService {

    private final SamplingBatchMapper batchMapper;
    private final SensorService sensorService;
    private final AuditChainService auditChainService;
    private final RedisDistributedLock distributedLock;
    private final PlatformTransactionManager transactionManager;

    public List<SamplingBatch> list(String sensorCode) {
        return batchMapper.selectList(new LambdaQueryWrapper<SamplingBatch>()
                .eq(sensorCode != null && !sensorCode.isBlank(), SamplingBatch::getSensorCode, sensorCode)
                .orderByDesc(SamplingBatch::getStartTime));
    }

    public SamplingBatch getById(Long id) {
        SamplingBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "采样批次不存在");
        }
        return batch;
    }

    public SamplingBatch getByNo(String batchNo) {
        SamplingBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<SamplingBatch>()
                .eq(SamplingBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "采样批次不存在: " + batchNo);
        }
        return batch;
    }

    /**
     * 重叠检测：返回与 [startTime, endTime] 重叠的同传感器批次。
     * 供查询时间区间和创建批次复用。
     */
    public List<SamplingBatch> findOverlaps(String sensorCode, LocalDateTime start, LocalDateTime end,
                                            Long excludeId) {
        return batchMapper.findOverlapping(sensorCode, start, end, excludeId);
    }

    /**
     * 入口不加事务：先取传感器维度 Redis 锁串行化，事务在锁内提交后再释放锁，
     * 消除重叠检测的并发窗口。
     */
    public SamplingBatch create(BatchCreateRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
        // 传感器必须存在
        sensorService.getByCode(request.getSensorCode());

        // 同传感器批次创建串行化，消除重叠检测的并发窗口
        return distributedLock.executeWithLock("batch-create:" + request.getSensorCode(),
                java.time.Duration.ofSeconds(10),
                () -> new TransactionTemplate(transactionManager).execute(status -> doCreate(request)));
    }

    private SamplingBatch doCreate(BatchCreateRequest request) {
        // 同一传感器时间区间重叠检测
        List<SamplingBatch> overlaps = findOverlaps(
                request.getSensorCode(), request.getStartTime(), request.getEndTime(), null);
        if (!overlaps.isEmpty()) {
            throw overlapException(overlaps);
        }

        SamplingBatch batch = new SamplingBatch();
        batch.setBatchNo("BATCH-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        batch.setSensorCode(request.getSensorCode());
        batch.setStartTime(request.getStartTime());
        batch.setEndTime(request.getEndTime());
        batch.setPurpose(request.getPurpose());
        batch.setOperator(request.getOperator() != null ? request.getOperator()
                : SecurityUtils.currentUsername());
        batch.setStatus("ACTIVE");
        batchMapper.insert(batch);

        auditChainService.append(SecurityUtils.currentUsername(), WebUtils.clientIp(),
                UUID.randomUUID().toString(), 0L,
                AuditConstants.ENTITY_BATCH, batch.getBatchNo(),
                AuditConstants.ACTION_BATCH_CREATE, null, batch);
        return batch;
    }

    private BusinessException overlapException(List<SamplingBatch> overlaps) {
        String detail = overlaps.stream()
                .map(b -> b.getBatchNo() + "[" + b.getStartTime() + " ~ " + b.getEndTime() + "]")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return new BusinessException(ErrorCode.BATCH_OVERLAP,
                ErrorCode.BATCH_OVERLAP.getMessage() + "，冲突批次: " + detail);
    }
}

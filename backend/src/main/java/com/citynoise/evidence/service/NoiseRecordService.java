package com.citynoise.evidence.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.citynoise.evidence.audit.AuditChainService;
import com.citynoise.evidence.audit.AuditConstants;
import com.citynoise.evidence.audit.EvidenceChainService;
import com.citynoise.evidence.common.BusinessException;
import com.citynoise.evidence.common.ErrorCode;
import com.citynoise.evidence.common.RedisDistributedLock;
import com.citynoise.evidence.common.WebUtils;
import com.citynoise.evidence.dto.AnomalyHandleRequest;
import com.citynoise.evidence.dto.EvidenceCreateRequest;
import com.citynoise.evidence.dto.RecordCreateRequest;
import com.citynoise.evidence.dto.RecordQuery;
import com.citynoise.evidence.dto.RecordTrendPoint;
import com.citynoise.evidence.dto.RecordVO;
import com.citynoise.evidence.entity.AnomalyEvent;
import com.citynoise.evidence.entity.EvidenceVersion;
import com.citynoise.evidence.entity.NoiseRecord;
import com.citynoise.evidence.entity.SamplingBatch;
import com.citynoise.evidence.mapper.AnomalyEventMapper;
import com.citynoise.evidence.mapper.NoiseRecordMapper;
import com.citynoise.evidence.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 噪声记录核心服务：
 * <ul>
 *   <li>Redis 分布式锁 + 应用查重 + 数据库唯一键三重防重，处理并发提交相同记录；</li>
 *   <li>单条/批量导入逐条独立事务，失败条目不写入、返回具体原因；</li>
 *   <li>超阈值自动生成异常事件（record_id+type 唯一键防重复异常）；</li>
 *   <li>创建即固化证据 v1 与审计链；异常处理、证据关联全部追加审计。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoiseRecordService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    private final NoiseRecordMapper recordMapper;
    private final AnomalyEventMapper anomalyMapper;
    private final com.citynoise.evidence.mapper.EvidenceVersionMapper evidenceVersionMapper;
    private final SensorService sensorService;
    private final BatchService batchService;
    private final AuditChainService auditChainService;
    private final EvidenceChainService evidenceChainService;
    private final RedisDistributedLock distributedLock;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.noise.anomaly-threshold}")
    private BigDecimal anomalyThreshold;

    // ============================ 查询 ============================

    public Page<RecordVO> pageRecords(RecordQuery query) {
        if (query.getStartTime() != null && query.getEndTime() != null
                && query.getEndTime().isBefore(query.getStartTime())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结束时间不能早于开始时间");
        }
        if (query.getDbMin() != null && query.getDbMax() != null
                && query.getDbMax().compareTo(query.getDbMin()) < 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "分贝上限不能小于下限");
        }
        Page<RecordVO> page = Page.of(query.safePage(), query.safeSize());
        return (Page<RecordVO>) recordMapper.selectRecordPage(page, query);
    }

    public NoiseRecord getEntity(Long id) {
        NoiseRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return record;
    }

    public RecordVO getDetail(Long id) {
        getEntity(id);
        RecordVO vo = recordMapper.selectRecordById(id);
        if (vo == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return vo;
    }

    public List<AnomalyEvent> listAnomalies(Long recordId) {
        return anomalyMapper.selectList(new LambdaQueryWrapper<AnomalyEvent>()
                .eq(AnomalyEvent::getRecordId, recordId)
                .orderByAsc(AnomalyEvent::getId));
    }

    public List<EvidenceVersion> listEvidences(Long recordId) {
        return evidenceVersionMapper.selectList(new LambdaQueryWrapper<EvidenceVersion>()
                .eq(EvidenceVersion::getRecordId, recordId)
                .orderByAsc(EvidenceVersion::getVersionNo));
    }

    public List<RecordTrendPoint> trend(String sensorCode, LocalDateTime start, LocalDateTime end) {
        return recordMapper.selectTrend(sensorCode, start, end);
    }

    // ============================ 单条创建 ============================

    /**
     * 入口不加事务：先取 Redis 锁串行化相同指纹提交，事务在锁内提交后再释放，
     * 消除“检查通过但未提交”的并发窗口；极端情况下数据库唯一键是最终防线。
     */
    public NoiseRecord create(RecordCreateRequest request) {
        normalize(request);
        String lockKey = fingerprint(request.getSensorCode(), request.getSampleTime(),
                request.getRawDataHash());
        return distributedLock.executeWithLock(lockKey, LOCK_TTL,
                () -> new TransactionTemplate(transactionManager).execute(status -> doCreate(request)));
    }

    /**
     * 在独立事务中创建一条记录（供批量导入逐行调用，失败仅回滚当前行）。
     */
    public NoiseRecord createInNewTransaction(RecordCreateRequest request) {
        normalize(request);
        String lockKey = fingerprint(request.getSensorCode(), request.getSampleTime(),
                request.getRawDataHash());
        return distributedLock.executeWithLock(lockKey, LOCK_TTL, () -> {
            TransactionTemplate tt = new TransactionTemplate(transactionManager);
            // 必须挂起导入外层事务：单行业务失败只回滚本行，已成功行保留
            tt.setPropagationBehavior(
                    org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return tt.execute(status -> doCreate(request));
        });
    }

    // ============================ 异常处理 / 证据关联 ============================

    public AnomalyEvent handleAnomaly(Long anomalyId, AnomalyHandleRequest request) {
        AnomalyEvent anomaly = anomalyMapper.selectById(anomalyId);
        if (anomaly == null) {
            throw new BusinessException(ErrorCode.ANOMALY_NOT_FOUND);
        }
        NoiseRecord record = getEntity(anomaly.getRecordId());

        AnomalyEvent before = copyAnomaly(anomaly);
        String operator = SecurityUtils.currentUsername();
        anomaly.setStatus(request.getStatus());
        anomaly.setHandleNote(request.getHandleNote());
        anomaly.setHandledBy(operator);
        anomaly.setHandledAt(LocalDateTime.now());
        anomalyMapper.updateById(anomaly);

        // 异常状态变更 + 处理说明必须落不可篡改审计
        auditChainService.append(operator, WebUtils.clientIp(), UUID.randomUUID().toString(),
                record.getId(), AuditConstants.ENTITY_ANOMALY, anomaly.getEventNo(),
                AuditConstants.ACTION_STATUS_CHANGE, before, anomaly);
        return anomaly;
    }

    public EvidenceVersion linkEvidence(Long recordId, EvidenceCreateRequest request) {
        NoiseRecord record = getEntity(recordId);
        String operator = SecurityUtils.currentUsername();
        return evidenceChainService.appendVersion(record, request.getFileUri(),
                request.getChangeNote(), operator, WebUtils.clientIp(),
                UUID.randomUUID().toString());
    }

    // ============================ 内部实现 ============================

    private NoiseRecord doCreate(RecordCreateRequest request) {
        // 1. 传感器必须存在
        sensorService.getByCode(request.getSensorCode());

        // 2. 批次校验（若指定）：批次必须存在且为 ACTIVE，传感器匹配，采样时间落在批次区间。
        //    所有校验失败都在任何写库/审计之前抛出统一业务异常，事务回滚不留残留。
        Long batchId = null;
        if (request.getBatchNo() != null && !request.getBatchNo().isBlank()) {
            SamplingBatch batch = batchService.getByNo(request.getBatchNo().trim());
            if (!BatchService.STATUS_ACTIVE.equals(batch.getStatus())) {
                throw new BusinessException(ErrorCode.BATCH_CLOSED,
                        ErrorCode.BATCH_CLOSED.getMessage() + ": " + batch.getBatchNo());
            }
            if (!batch.getSensorCode().equals(request.getSensorCode())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "批次不属于传感器 " + request.getSensorCode());
            }
            if (request.getSampleTime().isBefore(batch.getStartTime())
                    || request.getSampleTime().isAfter(batch.getEndTime())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "采样时间不在批次区间内: " + batch.getStartTime() + " ~ " + batch.getEndTime());
            }
            batchId = batch.getId();
        }

        // 3. 应用层查重（同传感器 + 同采样时间 + 同哈希）
        Long exists = recordMapper.selectCount(new LambdaQueryWrapper<NoiseRecord>()
                .eq(NoiseRecord::getSensorCode, request.getSensorCode())
                .eq(NoiseRecord::getSampleTime, request.getSampleTime())
                .eq(NoiseRecord::getRawDataHash, request.getRawDataHash().toLowerCase()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_RECORD);
        }

        // 4. 落库（唯一键 uk_sensor_time_hash 兜底并发竞态）
        NoiseRecord record = new NoiseRecord();
        record.setRecordNo("NR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        record.setSensorCode(request.getSensorCode());
        record.setBatchId(batchId);
        record.setSampleTime(request.getSampleTime());
        record.setLongitude(request.getLongitude());
        record.setLatitude(request.getLatitude());
        record.setDbValue(request.getDbValue());
        record.setSpectrumSummary(request.getSpectrumSummary());
        record.setRawDataHash(request.getRawDataHash().toLowerCase());
        record.setCreatedBy(SecurityUtils.currentUsername());
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_RECORD);
        }

        String operator = record.getCreatedBy();
        String ip = WebUtils.clientIp();
        String traceId = UUID.randomUUID().toString();

        // 5. 记录创建审计（链上第 1 条）
        auditChainService.append(operator, ip, traceId, record.getId(),
                AuditConstants.ENTITY_RECORD, record.getRecordNo(),
                AuditConstants.ACTION_CREATE, null, record);

        // 6. 超阈值自动生成异常事件（唯一键保证同一记录不重复生成相同类型异常）
        if (request.getDbValue().compareTo(anomalyThreshold) > 0) {
            createAnomalyIfAbsent(record, operator, ip, traceId);
        }

        // 7. 固化证据 v1（同时追加 EVIDENCE_LINK 审计）
        evidenceChainService.appendVersion(record,
                "oss://noise-raw/" + record.getSensorCode() + "/" + record.getRecordNo() + ".bin",
                "原始采样数据固化(版本1)", operator, ip, traceId);
        return record;
    }

    private void createAnomalyIfAbsent(NoiseRecord record, String operator, String ip, String traceId) {
        AnomalyEvent existing = anomalyMapper.selectOne(new LambdaQueryWrapper<AnomalyEvent>()
                .eq(AnomalyEvent::getRecordId, record.getId())
                .eq(AnomalyEvent::getAnomalyType, "HIGH_DECIBEL"));
        if (existing != null) {
            // 同一记录不重复生成相同类型异常
            return;
        }
        AnomalyEvent anomaly = new AnomalyEvent();
        anomaly.setEventNo("AE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        anomaly.setRecordId(record.getId());
        anomaly.setAnomalyType("HIGH_DECIBEL");
        anomaly.setThresholdValue(anomalyThreshold);
        anomaly.setDbValue(record.getDbValue());
        anomaly.setStatus("OPEN");
        anomaly.setDescription("分贝值 " + record.getDbValue() + " 超过阈值 " + anomalyThreshold + " dB(A)");
        try {
            anomalyMapper.insert(anomaly);
        } catch (DuplicateKeyException e) {
            // 并发下另一事务已生成：不重复
            return;
        }
        auditChainService.append(operator, ip, traceId, record.getId(),
                AuditConstants.ENTITY_ANOMALY, anomaly.getEventNo(),
                AuditConstants.ACTION_CREATE, null, anomaly);
    }

    private void normalize(RecordCreateRequest request) {
        request.setSensorCode(request.getSensorCode() == null ? null : request.getSensorCode().trim());
        if (request.getRawDataHash() != null) {
            request.setRawDataHash(request.getRawDataHash().trim().toLowerCase());
        }
    }

    private String fingerprint(String sensorCode, LocalDateTime sampleTime, String hash) {
        return "record:" + sensorCode + ":" + sampleTime + ":" + hash;
    }

    private AnomalyEvent copyAnomaly(AnomalyEvent src) {
        AnomalyEvent snap = new AnomalyEvent();
        snap.setId(src.getId());
        snap.setEventNo(src.getEventNo());
        snap.setRecordId(src.getRecordId());
        snap.setAnomalyType(src.getAnomalyType());
        snap.setStatus(src.getStatus());
        snap.setHandleNote(src.getHandleNote());
        snap.setHandledBy(src.getHandledBy());
        snap.setHandledAt(src.getHandledAt());
        return snap;
    }
}

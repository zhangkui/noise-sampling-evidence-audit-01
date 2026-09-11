package com.citynoise.evidence.service;

import com.citynoise.evidence.audit.AuditChainService;
import com.citynoise.evidence.audit.AuditConstants;
import com.citynoise.evidence.audit.EvidenceChainService;
import com.citynoise.evidence.common.BusinessException;
import com.citynoise.evidence.common.ErrorCode;
import com.citynoise.evidence.common.RedisDistributedLock;
import com.citynoise.evidence.dto.RecordCreateRequest;
import com.citynoise.evidence.entity.SamplingBatch;
import com.citynoise.evidence.mapper.AnomalyEventMapper;
import com.citynoise.evidence.mapper.EvidenceVersionMapper;
import com.citynoise.evidence.mapper.NoiseRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 新增记录的批次门禁：批次不存在 / 已关闭 / 传感器不匹配都返回统一业务异常，
 * 且失败时不留下记录、异常、证据或审计残留。
 */
@ExtendWith(MockitoExtension.class)
class NoiseRecordServiceBatchTest {

    @Mock
    private NoiseRecordMapper recordMapper;
    @Mock
    private AnomalyEventMapper anomalyMapper;
    @Mock
    private EvidenceVersionMapper evidenceVersionMapper;
    @Mock
    private SensorService sensorService;
    @Mock
    private BatchService batchService;
    @Mock
    private AuditChainService auditChainService;
    @Mock
    private EvidenceChainService evidenceChainService;
    @Mock
    private RedisDistributedLock distributedLock;
    @Mock
    private PlatformTransactionManager transactionManager;

    private NoiseRecordService recordService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        recordService = new NoiseRecordService(recordMapper, anomalyMapper, evidenceVersionMapper,
                sensorService, batchService, auditChainService, evidenceChainService,
                distributedLock, transactionManager);
        ReflectionTestUtils.setField(recordService, "anomalyThreshold", new BigDecimal("85"));
        // Redis 锁直接放行；事务模板直接执行回调
        when(distributedLock.executeWithLock(anyString(), any(), any()))
                .thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(2)).get());
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    private RecordCreateRequest request(String batchNo) {
        RecordCreateRequest req = new RecordCreateRequest();
        req.setSensorCode("S-NJ-001");
        req.setBatchNo(batchNo);
        req.setSampleTime(LocalDateTime.of(2026, 9, 1, 9, 0));
        req.setDbValue(new BigDecimal("70.0"));
        req.setRawDataHash("a".repeat(64));
        return req;
    }

    private SamplingBatch batch(String status, String sensorCode) {
        SamplingBatch b = new SamplingBatch();
        b.setId(7L);
        b.setBatchNo("BATCH-X");
        b.setSensorCode(sensorCode);
        b.setStartTime(LocalDateTime.of(2026, 9, 1, 8, 0));
        b.setEndTime(LocalDateTime.of(2026, 9, 1, 12, 0));
        b.setStatus(status);
        return b;
    }

    /** 失败路径不得留下记录、异常、证据或审计残留。 */
    private void assertNoResidue() {
        verify(recordMapper, never()).insert(any());
        verify(anomalyMapper, never()).insert(any());
        verify(evidenceVersionMapper, never()).insert(any());
        verifyNoInteractions(auditChainService, evidenceChainService);
    }

    @Test
    void createWithClosedBatch_rejected_andLeavesNoResidue() {
        when(batchService.getByNo("BATCH-X"))
                .thenReturn(batch(BatchService.STATUS_CLOSED, "S-NJ-001"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> recordService.create(request("BATCH-X")));

        assertEquals(ErrorCode.BATCH_CLOSED.getCode(), e.getCode());
        assertNoResidue();
    }

    @Test
    void createWithMissingBatch_rejected_andLeavesNoResidue() {
        when(batchService.getByNo("BATCH-NONE"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "采样批次不存在: BATCH-NONE"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> recordService.create(request("BATCH-NONE")));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        assertNoResidue();
    }

    @Test
    void createWithSensorMismatch_rejected_andLeavesNoResidue() {
        when(batchService.getByNo("BATCH-X"))
                .thenReturn(batch(BatchService.STATUS_ACTIVE, "S-NJ-002"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> recordService.create(request("BATCH-X")));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        assertNoResidue();
    }

    @Test
    void createWithActiveBatch_proceedsToInsertAndAudit() {
        when(batchService.getByNo("BATCH-X"))
                .thenReturn(batch(BatchService.STATUS_ACTIVE, "S-NJ-001"));
        when(recordMapper.selectCount(any())).thenReturn(0L);

        recordService.create(request("BATCH-X"));

        // 记录落库并关联批次，随后写创建审计与证据 v1
        verify(recordMapper).insert(argThat(r -> r.getBatchId() != null && r.getBatchId() == 7L));
        verify(auditChainService).append(any(), any(), any(), any(),
                eq(AuditConstants.ENTITY_RECORD), any(), eq(AuditConstants.ACTION_CREATE), isNull(), any());
        verify(evidenceChainService).appendVersion(any(), any(), any(), any(), any(), any());
    }
}

package com.citynoise.evidence.service;

import com.citynoise.evidence.audit.AuditChainService;
import com.citynoise.evidence.audit.AuditConstants;
import com.citynoise.evidence.common.BusinessException;
import com.citynoise.evidence.common.ErrorCode;
import com.citynoise.evidence.common.RedisDistributedLock;
import com.citynoise.evidence.entity.SamplingBatch;
import com.citynoise.evidence.mapper.NoiseRecordMapper;
import com.citynoise.evidence.mapper.SamplingBatchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 批次关闭：状态机约束（仅 ACTIVE → CLOSED）、重复关闭幂等、审计只追加一次。
 */
@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    @Mock
    private SamplingBatchMapper batchMapper;
    @Mock
    private NoiseRecordMapper recordMapper;
    @Mock
    private SensorService sensorService;
    @Mock
    private AuditChainService auditChainService;
    @Mock
    private RedisDistributedLock distributedLock;
    @Mock
    private PlatformTransactionManager transactionManager;

    private BatchService batchService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        batchService = new BatchService(batchMapper, recordMapper, sensorService,
                auditChainService, distributedLock, transactionManager);
        // Redis 锁直接放行；事务模板直接执行回调
        when(distributedLock.executeWithLock(anyString(), any(), any()))
                .thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(2)).get());
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    private SamplingBatch batch(String status) {
        SamplingBatch b = new SamplingBatch();
        b.setId(1L);
        b.setBatchNo("BATCH-TEST-001");
        b.setSensorCode("S-NJ-001");
        b.setStartTime(LocalDateTime.of(2026, 9, 1, 8, 0));
        b.setEndTime(LocalDateTime.of(2026, 9, 1, 12, 0));
        b.setStatus(status);
        return b;
    }

    @Test
    void closeActiveBatch_transitionsToClosed_andAppendsSingleAuditWithSnapshots() {
        when(batchMapper.selectById(1L)).thenReturn(batch(BatchService.STATUS_ACTIVE));
        when(batchMapper.closeIfActive(1L)).thenReturn(1);

        SamplingBatch closed = batchService.close(1L);

        assertEquals(BatchService.STATUS_CLOSED, closed.getStatus());
        // 审计恰好一次，before 快照为 ACTIVE、after 快照为 CLOSED
        verify(auditChainService).append(any(), any(), any(), eq(0L),
                eq(AuditConstants.ENTITY_BATCH), eq("BATCH-TEST-001"),
                eq(AuditConstants.ACTION_BATCH_CLOSE),
                argThat(before -> before instanceof SamplingBatch
                        && BatchService.STATUS_ACTIVE.equals(((SamplingBatch) before).getStatus())),
                argThat(after -> after instanceof SamplingBatch
                        && BatchService.STATUS_CLOSED.equals(((SamplingBatch) after).getStatus())));
    }

    @Test
    void closeAlreadyClosedBatch_isIdempotent_withoutDuplicateAudit() {
        when(batchMapper.selectById(1L)).thenReturn(batch(BatchService.STATUS_CLOSED));

        SamplingBatch result = batchService.close(1L);

        // 幂等返回当前状态，不追加重复审计节点
        assertEquals(BatchService.STATUS_CLOSED, result.getStatus());
        verify(batchMapper, never()).closeIfActive(any());
        verifyNoInteractions(auditChainService);
    }

    @Test
    void closeMissingBatch_throwsNotFound() {
        when(batchMapper.selectById(99L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> batchService.close(99L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        verifyNoInteractions(auditChainService);
    }

    @Test
    void closeWhenConditionalUpdateLoses_throwsConcurrentConflict_withoutAudit() {
        when(batchMapper.selectById(1L)).thenReturn(batch(BatchService.STATUS_ACTIVE));
        when(batchMapper.closeIfActive(1L)).thenReturn(0);

        BusinessException e = assertThrows(BusinessException.class, () -> batchService.close(1L));

        assertEquals(ErrorCode.CONCURRENT_CONFLICT.getCode(), e.getCode());
        verifyNoInteractions(auditChainService);
    }
}

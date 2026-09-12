package com.citynoise.evidence.service;

import com.citynoise.evidence.audit.AuditChainService;
import com.citynoise.evidence.common.BusinessException;
import com.citynoise.evidence.common.ErrorCode;
import com.citynoise.evidence.common.RedisDistributedLock;
import com.citynoise.evidence.dto.AnomalyStatusCountVO;
import com.citynoise.evidence.dto.BatchStatisticsVO;
import com.citynoise.evidence.entity.SamplingBatch;
import com.citynoise.evidence.mapper.NoiseRecordMapper;
import com.citynoise.evidence.mapper.SamplingBatchMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 批次汇总统计：空批次约定、跨批次隔离、含异常批次状态计数、
 * 不存在批次 404、CLOSED 可查看历史统计，且统计读取全程只读
 * （不触碰审计链）。
 */
@ExtendWith(MockitoExtension.class)
class BatchStatisticsServiceTest {

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

    @InjectMocks
    private BatchService batchService;

    private static final Long BATCH_ID = 7L;

    private SamplingBatch batch(String status) {
        SamplingBatch b = new SamplingBatch();
        b.setId(BATCH_ID);
        b.setBatchNo("BATCH-X");
        b.setSensorCode("S-NJ-001");
        b.setStartTime(LocalDateTime.of(2026, 9, 1, 8, 0));
        b.setEndTime(LocalDateTime.of(2026, 9, 1, 12, 0));
        b.setPurpose("夜间施工噪声普查");
        b.setOperator("operator");
        b.setStatus(status);
        b.setCreatedAt(LocalDateTime.of(2026, 8, 31, 18, 0));
        return b;
    }

    private BatchStatisticsVO recordStats(long count, String first, String last,
                                          String avg, String max, String min) {
        BatchStatisticsVO vo = new BatchStatisticsVO();
        vo.setRecordCount(count);
        if (first != null) {
            vo.setFirstSampleTime(LocalDateTime.parse(first));
            vo.setLastSampleTime(LocalDateTime.parse(last));
            vo.setAvgDb(new BigDecimal(avg));
            vo.setMaxDb(new BigDecimal(max));
            vo.setMinDb(new BigDecimal(min));
        }
        return vo;
    }

    private AnomalyStatusCountVO statusCount(String status, long count) {
        AnomalyStatusCountVO c = new AnomalyStatusCountVO();
        c.setStatus(status);
        c.setCount(count);
        return c;
    }

    // ---------------- 空批次 ----------------

    @Test
    void emptyBatch_recordCountZero_statsNull_anomaliesZero() {
        when(batchMapper.selectById(BATCH_ID)).thenReturn(batch(BatchService.STATUS_ACTIVE));
        // SQL 对空批次返回单行：COUNT=0，MIN/MAX/AVG 全为 NULL
        when(recordMapper.selectBatchRecordStats(BATCH_ID))
                .thenReturn(recordStats(0, null, null, null, null, null));
        when(recordMapper.selectBatchAnomalyStats(BATCH_ID)).thenReturn(List.of());

        BatchStatisticsVO vo = batchService.getStatistics(BATCH_ID);

        assertEquals(0L, vo.getRecordCount());
        assertNull(vo.getFirstSampleTime());
        assertNull(vo.getLastSampleTime());
        assertNull(vo.getAvgDb());
        assertNull(vo.getMaxDb());
        assertNull(vo.getMinDb());
        assertEquals(0L, vo.getAnomalyTotal());
        assertEquals(0L, vo.getAnomalyOpen());
        assertEquals(0L, vo.getAnomalyProcessing());
        assertEquals(0L, vo.getAnomalyResolved());
        assertEquals(0L, vo.getAnomalyIgnored());
        // 基本信息仍正常回填
        assertEquals("BATCH-X", vo.getBatchNo());
        assertEquals("S-NJ-001", vo.getSensorCode());
    }

    // ---------------- 跨批次隔离 ----------------

    @Test
    void statistics_aggregatedStrictlyByBatchId_otherBatchesNeverQueried() {
        when(batchMapper.selectById(BATCH_ID)).thenReturn(batch(BatchService.STATUS_ACTIVE));
        when(recordMapper.selectBatchRecordStats(BATCH_ID))
                .thenReturn(recordStats(3, "2026-09-01T08:05:00", "2026-09-01T09:05:00",
                        "72.50", "88.20", "60.10"));
        when(recordMapper.selectBatchAnomalyStats(BATCH_ID)).thenReturn(List.of());

        BatchStatisticsVO vo = batchService.getStatistics(BATCH_ID);

        // 仅按目标批次 id 聚合，绝不允许把其他批次（如 8L）带入结果
        verify(recordMapper).selectBatchRecordStats(BATCH_ID);
        verify(recordMapper).selectBatchAnomalyStats(BATCH_ID);
        verify(recordMapper, never()).selectBatchRecordStats(8L);
        verify(recordMapper, never()).selectBatchAnomalyStats(8L);
        // 结果就是该批次聚合行的值，不做跨批次合并
        assertEquals(3L, vo.getRecordCount());
        assertEquals(LocalDateTime.parse("2026-09-01T08:05:00"), vo.getFirstSampleTime());
        assertEquals(LocalDateTime.parse("2026-09-01T09:05:00"), vo.getLastSampleTime());
        assertEquals(0, new BigDecimal("72.50").compareTo(vo.getAvgDb()));
        assertEquals(0, new BigDecimal("88.20").compareTo(vo.getMaxDb()));
        assertEquals(0, new BigDecimal("60.10").compareTo(vo.getMinDb()));
    }

    // ---------------- 含异常批次 ----------------

    @Test
    void batchWithAnomalies_countsGroupedByStatus() {
        when(batchMapper.selectById(BATCH_ID)).thenReturn(batch(BatchService.STATUS_ACTIVE));
        when(recordMapper.selectBatchRecordStats(BATCH_ID))
                .thenReturn(recordStats(10, "2026-09-01T08:00:00", "2026-09-01T11:00:00",
                        "79.30", "96.00", "58.00"));
        when(recordMapper.selectBatchAnomalyStats(BATCH_ID)).thenReturn(List.of(
                statusCount("OPEN", 2),
                statusCount("PROCESSING", 1),
                statusCount("RESOLVED", 3),
                statusCount("IGNORED", 1)));

        BatchStatisticsVO vo = batchService.getStatistics(BATCH_ID);

        assertEquals(10L, vo.getRecordCount());
        assertEquals(7L, vo.getAnomalyTotal());
        assertEquals(2L, vo.getAnomalyOpen());
        assertEquals(1L, vo.getAnomalyProcessing());
        assertEquals(3L, vo.getAnomalyResolved());
        assertEquals(1L, vo.getAnomalyIgnored());
    }

    @Test
    void unknownAnomalyStatus_countedInTotalButNotInKnownBuckets() {
        when(batchMapper.selectById(BATCH_ID)).thenReturn(batch(BatchService.STATUS_ACTIVE));
        when(recordMapper.selectBatchRecordStats(BATCH_ID))
                .thenReturn(recordStats(5, "2026-09-01T08:00:00", "2026-09-01T09:00:00",
                        "70.00", "90.00", "60.00"));
        // 将来新增状态时不丢数：计入总数，只是不落到四个已知字段
        when(recordMapper.selectBatchAnomalyStats(BATCH_ID)).thenReturn(List.of(
                statusCount("OPEN", 1), statusCount("REOPENED", 2)));

        BatchStatisticsVO vo = batchService.getStatistics(BATCH_ID);

        assertEquals(3L, vo.getAnomalyTotal());
        assertEquals(1L, vo.getAnomalyOpen());
        assertEquals(0L, vo.getAnomalyProcessing());
        assertEquals(0L, vo.getAnomalyResolved());
        assertEquals(0L, vo.getAnomalyIgnored());
    }

    // ---------------- 不存在批次 ----------------

    @Test
    void missingBatch_throwsNotFound_andRunsNoAggregationOrAudit() {
        when(batchMapper.selectById(404L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> batchService.getStatistics(404L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        verify(recordMapper, never()).selectBatchRecordStats(any());
        verify(recordMapper, never()).selectBatchAnomalyStats(any());
        verifyNoInteractions(auditChainService);    }

    // ---------------- CLOSED 批次仍可查看 ----------------

    @Test
    void closedBatch_statisticsStillViewable() {
        when(batchMapper.selectById(BATCH_ID)).thenReturn(batch(BatchService.STATUS_CLOSED));
        when(recordMapper.selectBatchRecordStats(BATCH_ID))
                .thenReturn(recordStats(2, "2026-09-01T08:00:00", "2026-09-01T08:30:00",
                        "65.00", "70.00", "60.00"));
        when(recordMapper.selectBatchAnomalyStats(BATCH_ID)).thenReturn(List.of());

        BatchStatisticsVO vo = batchService.getStatistics(BATCH_ID);

        assertEquals(BatchService.STATUS_CLOSED, vo.getStatus());
        assertEquals(2L, vo.getRecordCount());
    }

    // ---------------- 只读：统计不改任何数据/审计 ----------------

    @Test
    void statistics_isReadOnly_noAuditChainMutation() {
        when(batchMapper.selectById(BATCH_ID)).thenReturn(batch(BatchService.STATUS_ACTIVE));
        when(recordMapper.selectBatchRecordStats(BATCH_ID))
                .thenReturn(recordStats(1, "2026-09-01T08:00:00", "2026-09-01T08:00:00",
                        "70.00", "70.00", "70.00"));
        when(recordMapper.selectBatchAnomalyStats(BATCH_ID))
                .thenReturn(List.of(statusCount("OPEN", 1)));

        batchService.getStatistics(BATCH_ID);

        // 纯读：批次表不写、审计链完全不参与
        verify(batchMapper, never()).insert(any());
        verify(batchMapper, never()).updateById(any());
        verifyNoInteractions(auditChainService);
    }
}

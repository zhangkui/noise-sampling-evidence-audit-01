package com.citynoise.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采样批次汇总统计。
 *
 * <p><b>空批次约定</b>（批次存在但没有任何未删除记录）：
 * {@code recordCount = 0}；{@code firstSampleTime}、{@code lastSampleTime}、
 * {@code avgDb}、{@code maxDb}、{@code minDb} 均为 {@code null}；
 * 异常计数全部为 0。前端据此展示“—”而不是 0 dB。</p>
 */
@Data
public class BatchStatisticsVO {

    // ---------------- 批次基本信息 ----------------

    private Long id;
    private String batchNo;
    private String sensorCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String purpose;
    private String operator;
    /** ACTIVE / CLOSED（CLOSED 批次同样可查看历史统计） */
    private String status;
    private LocalDateTime createdAt;

    // ---------------- 记录统计（仅统计本批次、未删除记录） ----------------

    /** 记录总数；空批次为 0 */
    private Long recordCount;
    /** 最早采样时间；空批次为 null */
    private LocalDateTime firstSampleTime;
    /** 最晚采样时间；空批次为 null */
    private LocalDateTime lastSampleTime;
    /** 平均分贝（数据库端聚合，ROUND 2 位）；空批次为 null */
    private BigDecimal avgDb;
    /** 最大分贝；空批次为 null */
    private BigDecimal maxDb;
    /** 最小分贝；空批次为 null */
    private BigDecimal minDb;

    // ---------------- 异常统计（本批次记录关联的异常事件） ----------------

    /** 异常事件总数（含全部状态）；空批次/无异常为 0 */
    private Long anomalyTotal;
    /** 待处理 OPEN */
    private Long anomalyOpen;
    /** 处理中 PROCESSING */
    private Long anomalyProcessing;
    /** 已解决 RESOLVED */
    private Long anomalyResolved;
    /** 已忽略 IGNORED */
    private Long anomalyIgnored;
}

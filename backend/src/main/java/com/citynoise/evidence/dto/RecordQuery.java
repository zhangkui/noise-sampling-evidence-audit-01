package com.citynoise.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 噪声记录查询条件：分页 + 时间区间 + 传感器编号 + 异常状态 + 分贝区间。
 */
@Data
public class RecordQuery {

    private Long page = 1L;
    private Long size = 10L;
    /** 按采样批次精确过滤（从批次详情跳转时携带） */
    private Long batchId;
    private String sensorCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** OPEN / PROCESSING / RESOLVED / IGNORED / NONE(无异常) / ANY(有异常) */
    private String anomalyStatus;
    private BigDecimal dbMin;
    private BigDecimal dbMax;

    public long safePage() {
        return page == null || page < 1 ? 1 : Math.min(page, 10000);
    }

    public long safeSize() {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 100);
    }
}

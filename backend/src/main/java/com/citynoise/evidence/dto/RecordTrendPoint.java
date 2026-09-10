package com.citynoise.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecordTrendPoint {
    /** 小时桶，格式 yyyy-MM-dd HH:00 */
    private String timeBucket;
    private BigDecimal avgDb;
    private BigDecimal maxDb;
    private BigDecimal minDb;
    private Integer sampleCount;
}

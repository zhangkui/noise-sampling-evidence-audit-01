package com.citynoise.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 批量导入单条结果。
 */
@Data
public class ImportItemResult {
    private Integer rowIndex;
    private String sensorCode;
    private String sampleTime;
    private BigDecimal dbValue;
    private Boolean success;
    private String failReason;
    private Long recordId;
    private String recordNo;
}

package com.citynoise.evidence.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RecordVO {
    private Long id;
    private String recordNo;
    private String sensorCode;
    private Long batchId;
    private String batchNo;
    private LocalDateTime sampleTime;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal dbValue;
    private String spectrumSummary;
    private String rawDataHash;
    private String createdBy;
    private LocalDateTime createdAt;

    /** 该记录当前异常状态：OPEN/PROCESSING/RESOLVED/IGNORED，无异常为 NONE */
    private String anomalyStatus;
    private Long anomalyId;
}

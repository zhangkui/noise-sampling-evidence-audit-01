package com.citynoise.evidence.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RecordCreateRequest {

    @NotBlank(message = "传感器编号不能为空")
    @Size(max = 64, message = "传感器编号长度不能超过64")
    private String sensorCode;

    /** 可选：所属批次编号；提供后采样时间必须落在批次区间内 */
    private String batchNo;

    @NotNull(message = "采样时间不能为空")
    private LocalDateTime sampleTime;

    @DecimalMin(value = "-180.0", message = "经度不合法")
    @DecimalMax(value = "180.0", message = "经度不合法")
    private BigDecimal longitude;

    @DecimalMin(value = "-90.0", message = "纬度不合法")
    @DecimalMax(value = "90.0", message = "纬度不合法")
    private BigDecimal latitude;

    @NotNull(message = "分贝值不能为空")
    @DecimalMin(value = "0.0", message = "分贝值必须在 0~200 之间")
    @DecimalMax(value = "200.0", message = "分贝值必须在 0~200 之间")
    private BigDecimal dbValue;

    @Size(max = 2000, message = "频谱摘要过长")
    private String spectrumSummary;

    @NotBlank(message = "原始数据哈希不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "原始数据哈希必须为 64 位十六进制 SHA-256")
    private String rawDataHash;
}

package com.citynoise.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BatchCreateRequest {

    @NotBlank(message = "传感器编号不能为空")
    @Size(max = 64)
    private String sensorCode;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @Size(max = 255)
    private String purpose;

    @Size(max = 64)
    private String operator;
}

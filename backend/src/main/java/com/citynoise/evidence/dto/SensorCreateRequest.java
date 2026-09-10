package com.citynoise.evidence.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SensorCreateRequest {

    @NotBlank(message = "传感器编号不能为空")
    @Size(max = 64)
    private String sensorCode;

    @NotBlank(message = "传感器名称不能为空")
    @Size(max = 128)
    private String name;

    @Size(max = 255)
    private String location;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal longitude;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal latitude;
}

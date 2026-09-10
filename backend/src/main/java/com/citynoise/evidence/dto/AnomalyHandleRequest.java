package com.citynoise.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnomalyHandleRequest {

    /** 目标状态：PROCESSING / RESOLVED / IGNORED */
    @NotBlank(message = "处理状态不能为空")
    @Pattern(regexp = "PROCESSING|RESOLVED|IGNORED", message = "处理状态只能为 PROCESSING/RESOLVED/IGNORED")
    private String status;

    @NotBlank(message = "处理说明不能为空")
    @Size(max = 1000, message = "处理说明长度不能超过1000")
    private String handleNote;
}

package com.citynoise.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EvidenceCreateRequest {

    @NotBlank(message = "证据文件地址不能为空")
    @Size(max = 255, message = "证据文件地址过长")
    private String fileUri;

    @Size(max = 500, message = "版本说明过长")
    private String changeNote;
}

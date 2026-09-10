package com.citynoise.evidence.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量导入请求：逐条校验、逐条返回失败原因，任何一条失败都不写入该条。
 * 注意：records 不加 @Valid 级联校验 —— 改由 ImportService 逐行校验，
 * 避免单行非法导致整批 400 而拿不到逐行失败原因。
 */
@Data
public class RecordImportRequest {

    @Size(max = 255)
    private String fileName;

    @NotEmpty(message = "导入数据不能为空")
    private List<RecordCreateRequest> records;
}

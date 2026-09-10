package com.citynoise.evidence.dto;

import lombok.Data;

import java.util.List;

@Data
public class ImportResultVO {
    private Long taskId;
    private String importNo;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    /** DONE 全部成功 / PARTIAL 部分成功 / ALL_FAILED 全部失败 */
    private String status;
    private List<ImportItemResult> items;
}

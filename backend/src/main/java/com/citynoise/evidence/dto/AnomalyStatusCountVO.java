package com.citynoise.evidence.dto;

import lombok.Data;

/**
 * 异常状态计数（批次汇总按状态 GROUP BY 的一行）。
 */
@Data
public class AnomalyStatusCountVO {

    /** OPEN / PROCESSING / RESOLVED / IGNORED（及可能出现的其他状态） */
    private String status;
    private Long count;
}

package com.citynoise.evidence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志：仅追加。不提供 update/delete 接口，表中也无更新时间字段。
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId
    private Long id;

    /** 同一噪声记录链内的连续序号，从 1 开始 */
    private Integer chainSeq;
    private String traceId;
    private Long recordId;
    private String entityType;
    private String entityId;
    private String action;
    private String beforeJson;
    private String afterJson;
    private String operator;
    private String operatorIp;
    private String prevHash;
    private String entryHash;
    private String hashPayload;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

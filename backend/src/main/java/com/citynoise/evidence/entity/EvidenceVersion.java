package com.citynoise.evidence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("evidence_version")
public class EvidenceVersion {

    @TableId
    private Long id;

    private Long recordId;
    private Integer versionNo;
    private String algorithm;
    private String prevHash;
    private String evidenceHash;
    private String fileUri;
    private String changeNote;
    private String hashPayload;
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

package com.citynoise.evidence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 哈希链链头：每个业务链一行（审计链 / 证据链）。
 */
@Data
@TableName("audit_chain")
public class ChainHead {

    @TableId
    private String chainKey;
    private Integer lastSeq;
    private String lastHash;
    private LocalDateTime updatedAt;
}

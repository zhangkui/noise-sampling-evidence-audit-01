package com.citynoise.evidence.dto;

import lombok.Data;

import java.util.List;

/**
 * 按记录回放完整变更历史：记录 + 证据版本 + 审计时间线（已做链校验）。
 */
@Data
public class RecordHistoryVO {
    private RecordVO record;
    private List<EvidenceVersion> evidences;
    private List<AuditTimelineItem> timeline;
    /** 哈希链是否完整：重算任一节点不一致则为 false */
    private Boolean chainValid;
    /** 第一个断裂节点说明，null 表示完整 */
    private String brokenAt;

    @Data
    public static class EvidenceVersion {
        private Long id;
        private Integer versionNo;
        private String algorithm;
        private String prevHash;
        private String evidenceHash;
        private String fileUri;
        private String changeNote;
        private String createdBy;
        private String createdAt;
    }

    @Data
    public static class AuditTimelineItem {
        private Long id;
        private Integer chainSeq;
        private String entityType;
        private String entityId;
        private String action;
        private String beforeJson;
        private String afterJson;
        private String operator;
        private String prevHash;
        private String entryHash;
        private String createdAt;
    }
}

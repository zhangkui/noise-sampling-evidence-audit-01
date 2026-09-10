package com.citynoise.evidence.audit;

import com.citynoise.evidence.entity.AuditLog;

/**
 * 审计追加参数。
 */
public record AuditEntry(
        Long recordId,
        String entityType,
        String entityId,
        String action,
        String beforeJson,
        String afterJson
) {

    public static AuditEntry of(Long recordId, String entityType, String entityId, String action,
                                String beforeJson, String afterJson) {
        return new AuditEntry(recordId, entityType, entityId, action, beforeJson, afterJson);
    }

    public AuditLog toLog() {
        AuditLog log = new AuditLog();
        log.setRecordId(recordId());
        log.setEntityType(entityType());
        log.setEntityId(entityId());
        log.setAction(action());
        log.setBeforeJson(beforeJson());
        log.setAfterJson(afterJson());
        return log;
    }
}

package com.citynoise.evidence.audit;

/**
 * 审计实体类型与动作常量。
 */
public final class AuditConstants {

    private AuditConstants() {
    }

    public static final String ENTITY_RECORD = "RECORD";
    public static final String ENTITY_ANOMALY = "ANOMALY";
    public static final String ENTITY_EVIDENCE = "EVIDENCE";
    public static final String ENTITY_BATCH = "BATCH";
    public static final String ENTITY_SENSOR = "SENSOR";
    public static final String ENTITY_IMPORT = "IMPORT";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_STATUS_CHANGE = "STATUS_CHANGE";
    public static final String ACTION_HANDLE = "HANDLE";
    public static final String ACTION_EVIDENCE_LINK = "EVIDENCE_LINK";
    public static final String ACTION_BATCH_CREATE = "BATCH_CREATE";
    public static final String ACTION_IMPORT = "IMPORT";
    public static final String ACTION_SENSOR_CREATE = "SENSOR_CREATE";
}

-- =====================================================================
-- 城市噪声传感器采样证据链系统 - 表结构
-- 应用启动时自动执行（CREATE TABLE IF NOT EXISTS，不会清空已有数据）
-- =====================================================================

SET NAMES utf8mb4;

-- 系统用户
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(64)  NOT NULL COMMENT '登录账号',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    real_name   VARCHAR(64)           DEFAULT NULL COMMENT '姓名',
    role        VARCHAR(32)  NOT NULL DEFAULT 'OPERATOR' COMMENT 'ADMIN / OPERATOR',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户';

-- 传感器
CREATE TABLE IF NOT EXISTS sensor (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    sensor_code  VARCHAR(64)   NOT NULL COMMENT '传感器编号',
    name         VARCHAR(128)  NOT NULL COMMENT '传感器名称',
    location     VARCHAR(255)           DEFAULT NULL COMMENT '安装位置描述',
    longitude    DECIMAL(10, 7)         DEFAULT NULL COMMENT '经度',
    latitude     DECIMAL(10, 7)         DEFAULT NULL COMMENT '纬度',
    status       VARCHAR(16)   NOT NULL DEFAULT 'ONLINE' COMMENT 'ONLINE/OFFLINE/MAINTENANCE',
    created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted      TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sensor_code (sensor_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='噪声传感器';

-- 采样批次
CREATE TABLE IF NOT EXISTS sampling_batch (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    batch_no     VARCHAR(64)  NOT NULL COMMENT '批次编号',
    sensor_code  VARCHAR(64)  NOT NULL COMMENT '传感器编号',
    start_time   DATETIME(3)  NOT NULL COMMENT '采样开始时间',
    end_time     DATETIME(3)  NOT NULL COMMENT '采样结束时间',
    purpose      VARCHAR(255)          DEFAULT NULL COMMENT '采样目的',
    operator     VARCHAR(64)           DEFAULT NULL COMMENT '负责人',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CLOSED',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted      TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_no (batch_no),
    KEY idx_batch_sensor_time (sensor_code, start_time, end_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='采样批次';

-- 噪声采样记录（原始数据不可修改）
CREATE TABLE IF NOT EXISTS noise_record (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    record_no         VARCHAR(64)   NOT NULL COMMENT '记录编号',
    sensor_code       VARCHAR(64)   NOT NULL COMMENT '传感器编号',
    batch_id          BIGINT                DEFAULT NULL COMMENT '所属采样批次',
    sample_time       DATETIME(3)   NOT NULL COMMENT '采样时间',
    longitude         DECIMAL(10, 7)         DEFAULT NULL COMMENT '采样点经度',
    latitude          DECIMAL(10, 7)         DEFAULT NULL COMMENT '采样点纬度',
    db_value          DECIMAL(5, 2) NOT NULL COMMENT '分贝值 dB(A)',
    spectrum_summary  VARCHAR(2000)          DEFAULT NULL COMMENT '频谱摘要(JSON)',
    raw_data_hash     VARCHAR(64)   NOT NULL COMMENT '原始数据 SHA-256',
    created_by        VARCHAR(64)            DEFAULT NULL COMMENT '录入人',
    created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted           TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_no (record_no),
    -- 同一传感器、相同采样时间、相同原始哈希不得重复入库（数据库层最终防线）
    UNIQUE KEY uk_sensor_time_hash (sensor_code, sample_time, raw_data_hash),
    KEY idx_record_sensor_time (sensor_code, sample_time),
    KEY idx_record_batch (batch_id),
    KEY idx_record_db (db_value)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='噪声采样记录';

-- 异常事件
CREATE TABLE IF NOT EXISTS anomaly_event (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    event_no         VARCHAR(64)   NOT NULL COMMENT '事件编号',
    record_id        BIGINT        NOT NULL COMMENT '关联噪声记录',
    anomaly_type     VARCHAR(32)   NOT NULL DEFAULT 'HIGH_DECIBEL' COMMENT '异常类型',
    threshold_value  DECIMAL(5, 2) NOT NULL COMMENT '触发阈值',
    db_value         DECIMAL(5, 2) NOT NULL COMMENT '触发时分贝值',
    status           VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PROCESSING/RESOLVED/IGNORED',
    description      VARCHAR(500)           DEFAULT NULL COMMENT '异常描述',
    handle_note      VARCHAR(1000)          DEFAULT NULL COMMENT '处理说明',
    handled_by       VARCHAR(64)            DEFAULT NULL COMMENT '处理人',
    handled_at       DATETIME(3)            DEFAULT NULL COMMENT '处理时间',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_no (event_no),
    -- 同一记录不能重复生成相同类型异常
    UNIQUE KEY uk_record_type (record_id, anomaly_type),
    KEY idx_anomaly_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='噪声异常事件';

-- 证据版本（哈希链）
CREATE TABLE IF NOT EXISTS evidence_version (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    record_id      BIGINT       NOT NULL COMMENT '关联噪声记录',
    version_no     INT          NOT NULL COMMENT '版本号(从1开始)',
    algorithm      VARCHAR(32)  NOT NULL DEFAULT 'SHA-256',
    prev_hash      VARCHAR(64)  NOT NULL COMMENT '上一版本哈希',
    evidence_hash  VARCHAR(64)  NOT NULL COMMENT '本版本证据哈希',
    file_uri       VARCHAR(255)          DEFAULT NULL COMMENT '原始文件/对象存储地址',
    change_note    VARCHAR(500)          DEFAULT NULL COMMENT '版本说明',
    hash_payload   VARCHAR(2000)         DEFAULT NULL COMMENT '哈希计算原文(便于核验)',
    created_by     VARCHAR(64)           DEFAULT NULL,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_evidence_record_version (record_id, version_no),
    KEY idx_evidence_record (record_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='证据版本';

-- 哈希链链头（每个业务链一行，行锁保证并发下序号/前驱哈希严格连续）
CREATE TABLE IF NOT EXISTS audit_chain (
    chain_key   VARCHAR(96)  NOT NULL COMMENT '链标识: rec:{id} / evidence:{id} / batch:{id} / import:{id}',
    last_seq    INT          NOT NULL DEFAULT 0 COMMENT '链内最后序号',
    last_hash   VARCHAR(64)  NOT NULL COMMENT '链尾哈希',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (chain_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='哈希链链头';

-- 审计日志（仅追加、不可修改/删除，哈希链防篡改）
CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    chain_seq      INT          NOT NULL COMMENT '该记录链内序号(从1开始)',
    trace_id       VARCHAR(64)           DEFAULT NULL COMMENT '操作链路ID',
    record_id      BIGINT                DEFAULT NULL COMMENT '关联噪声记录',
    entity_type    VARCHAR(32)  NOT NULL COMMENT 'RECORD/ANOMALY/EVIDENCE/BATCH/SENSOR/IMPORT',
    entity_id      VARCHAR(64)           DEFAULT NULL COMMENT '实体标识',
    action         VARCHAR(64)  NOT NULL COMMENT 'CREATE/UPDATE/STATUS_CHANGE/EVIDENCE_LINK/IMPORT/DELETE',
    before_json    TEXT                  DEFAULT NULL COMMENT '变更前快照',
    after_json     TEXT                  DEFAULT NULL COMMENT '变更后快照',
    operator       VARCHAR(64)  NOT NULL COMMENT '操作人',
    operator_ip    VARCHAR(64)           DEFAULT NULL,
    prev_hash      VARCHAR(64)  NOT NULL COMMENT '链上前一审计条目哈希',
    entry_hash     VARCHAR(64)  NOT NULL COMMENT '本条目哈希',
    hash_payload   TEXT                  DEFAULT NULL COMMENT '哈希计算原文',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_record_seq (record_id, chain_seq),
    KEY idx_audit_entity (entity_type, entity_id),
    KEY idx_audit_time (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='审计日志(追加写)';

-- 批量导入任务
CREATE TABLE IF NOT EXISTS import_task (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    import_no      VARCHAR(64)  NOT NULL COMMENT '导入任务编号',
    file_name      VARCHAR(255)          DEFAULT NULL,
    total_count    INT          NOT NULL DEFAULT 0,
    success_count  INT          NOT NULL DEFAULT 0,
    fail_count     INT          NOT NULL DEFAULT 0,
    status         VARCHAR(16)  NOT NULL DEFAULT 'DONE' COMMENT 'DONE/PARTIAL/ALL_FAILED',
    operator       VARCHAR(64)           DEFAULT NULL,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_no (import_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='批量导入任务';

-- 批量导入明细（逐条记录成功/失败原因）
CREATE TABLE IF NOT EXISTS import_item (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    task_id      BIGINT       NOT NULL,
    row_index    INT          NOT NULL COMMENT '行号(从1开始)',
    record_no    VARCHAR(64)           DEFAULT NULL,
    sensor_code  VARCHAR(64)           DEFAULT NULL,
    sample_time  VARCHAR(40)           DEFAULT NULL,
    db_value     DECIMAL(5, 2)          DEFAULT NULL,
    success      TINYINT(1)   NOT NULL DEFAULT 0,
    fail_reason  VARCHAR(500)          DEFAULT NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_import_item_task (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='批量导入明细';

-- =====================================================================
-- 兼容已有数据库的增量迁移（幂等、可重复执行）
-- 老库由早期 CREATE TABLE 建表，缺少批次维度索引；这里通过 information_schema
-- 判断后再决定是否添加，已存在时执行空操作，重复启动不会报错、不影响已有数据。
-- 新库的索引已包含在上文建表语句中，此处检测到存在会自动跳过。
-- =====================================================================
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'noise_record'
      AND index_name = 'idx_record_batch'
);
SET @ddl := IF(@idx_exists = 0,
               'ALTER TABLE noise_record ADD INDEX idx_record_batch (batch_id)',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

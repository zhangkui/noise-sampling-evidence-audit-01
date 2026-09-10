package com.citynoise.evidence.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.citynoise.evidence.audit.EvidenceChainService;
import com.citynoise.evidence.common.HashUtils;
import com.citynoise.evidence.dto.AnomalyHandleRequest;
import com.citynoise.evidence.dto.BatchCreateRequest;
import com.citynoise.evidence.dto.RecordCreateRequest;
import com.citynoise.evidence.dto.RecordImportRequest;
import com.citynoise.evidence.dto.SensorCreateRequest;
import com.citynoise.evidence.entity.AnomalyEvent;
import com.citynoise.evidence.entity.SysUser;
import com.citynoise.evidence.mapper.AnomalyEventMapper;
import com.citynoise.evidence.mapper.SysUserMapper;
import com.citynoise.evidence.service.BatchService;
import com.citynoise.evidence.service.ImportService;
import com.citynoise.evidence.service.NoiseRecordService;
import com.citynoise.evidence.service.SensorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动时自动初始化表结构（schema.sql）与演示数据。
 * 已初始化过（存在 admin 账号）则跳过，保证可重复启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SensorService sensorService;
    private final BatchService batchService;
    private final NoiseRecordService recordService;
    private final ImportService importService;
    private final EvidenceChainService evidenceChainService;
    private final AnomalyEventMapper anomalyMapper;

    @Value("${app.init.demo-data:true}")
    private boolean demoData;

    @Override
    public void run(ApplicationArguments args) {
        Long adminCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "admin"));
        if (adminCount != null && adminCount > 0) {
            log.info("演示数据已存在，跳过初始化");
            return;
        }
        if (!demoData) {
            log.info("演示数据初始化已关闭(app.init.demo-data=false)，仅建表");
            return;
        }
        log.info("开始初始化演示数据...");
        initUsers();
        initSensorsBatchesAndRecords();
        initDemoImport();
        log.info("演示数据初始化完成");
    }

    private void initUsers() {
        createUser("admin", "admin123", "系统管理员", "ADMIN");
        createUser("operator", "operator123", "监测操作员", "OPERATOR");
    }

    private void createUser(String username, String rawPassword, String realName, String role) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRealName(realName);
        user.setRole(role);
        user.setEnabled(1);
        userMapper.insert(user);
    }

    private void initSensorsBatchesAndRecords() {
        // 4 个传感器
        createSensor("S-NJ-001", "新街口1号噪声站", "南京市玄武区新街口", 118.77880, 32.04170);
        createSensor("S-NJ-002", "鼓楼广场2号站", "南京市鼓楼区鼓楼广场", 118.76945, 32.06282);
        createSensor("S-NJ-003", "夫子庙3号站", "南京市秦淮区夫子庙", 118.78990, 32.01980);
        createSensor("S-NJ-004", "河西大街4号站", "南京市建邺区河西大街", 118.72560, 32.00530);

        // 同一传感器两个不重叠批次（用于对比“重叠冲突”演示）
        String b1 = createBatch("S-NJ-001",
                LocalDateTime.of(2026, 9, 1, 8, 0),
                LocalDateTime.of(2026, 9, 1, 12, 0), "工作日早高峰监测");
        String b2 = createBatch("S-NJ-001",
                LocalDateTime.of(2026, 9, 1, 13, 0),
                LocalDateTime.of(2026, 9, 1, 17, 0), "工作日午间监测");
        createBatch("S-NJ-002",
                LocalDateTime.of(2026, 9, 2, 8, 0),
                LocalDateTime.of(2026, 9, 2, 20, 0), "全天噪声普查");

        // S-NJ-001：跨 9 天的小时级样本，分贝在 58~93 间波动，部分超阈值 85
        double[] dbPattern = {62.5, 68.2, 71.0, 78.4, 83.6, 88.9, 92.3, 79.5, 74.1, 69.8,
                65.2, 70.4, 76.8, 84.1, 87.7, 90.2, 81.3, 73.0};
        List<Long> recordIds = new ArrayList<>();
        int idx = 0;
        for (int day = 1; day <= 9; day++) {
            for (int hour = 0; hour < 2; hour++) {
                LocalDateTime time = LocalDateTime.of(2026, 9, day, 9 + hour * 4, 0);
                String batchNo = (day == 1 && (hour == 0)) ? b1 : (day == 1 && hour == 1 ? b2 : null);
                BigDecimal db = BigDecimal.valueOf(dbPattern[idx % dbPattern.length]);
                RecordCreateRequest req = buildRecord("S-NJ-001", time, db, batchNo);
                recordIds.add(recordService.createInNewTransaction(req).getId());
                idx++;
            }
        }

        // S-NJ-002：安静样本 + 一条超标样本
        recordService.createInNewTransaction(buildRecord("S-NJ-002",
                LocalDateTime.of(2026, 9, 2, 10, 0), BigDecimal.valueOf(55.6), null));
        Long loudId = recordService.createInNewTransaction(buildRecord("S-NJ-002",
                LocalDateTime.of(2026, 9, 2, 19, 30), BigDecimal.valueOf(91.8), null)).getId();

        // 异常处理演示：把最早生成的一条异常标记为 RESOLVED，另一条 PROCESSING
        List<AnomalyEvent> anomalies = anomalyMapper.selectList(
                new LambdaQueryWrapper<AnomalyEvent>().orderByAsc(AnomalyEvent::getId));
        if (anomalies.size() >= 2) {
            handle(anomalies.get(0).getId(), "RESOLVED",
                    "现场核查为广场舞音响噪声，已劝导体谅降音，复测 72dB，恢复正常。");
            handle(anomalies.get(1).getId(), "PROCESSING",
                    "已联系城管中队现场处置，等待复测结果。");
        }

        // 证据版本演示：给一条超标记录追加证据 v2，并为 loudId 追加证据
        recordService.linkEvidence(recordIds.get(5), evidenceReq(
                "oss://noise-raw/S-NJ-001/evidence-v2-spectrogram.png",
                "追加频谱图取证附件(版本2)"));
        recordService.linkEvidence(loudId, evidenceReq(
                "oss://noise-raw/S-NJ-002/evidence-v2-video.mp4",
                "执法记录仪视频证据(版本2)"));
    }

    /**
     * 构造一批混合结果的导入任务：1 成功、1 库内重复、1 字段非法、1 传感器不存在。
     */
    private void initDemoImport() {
        RecordImportRequest importReq = new RecordImportRequest();
        importReq.setFileName("demo-import-sample.xlsx");
        List<RecordCreateRequest> rows = new ArrayList<>();

        // 1) 合法新记录 → 成功
        rows.add(buildRecord("S-NJ-003", LocalDateTime.of(2026, 9, 5, 10, 0),
                BigDecimal.valueOf(70.0), null));
        // 2) 与库内某记录同传感器/同时间/同哈希 → 重复失败
        rows.add(buildRecord("S-NJ-002", LocalDateTime.of(2026, 9, 2, 10, 0),
                BigDecimal.valueOf(55.6), null));
        // 3) 分贝超范围 → 字段校验失败
        RecordCreateRequest invalid = buildRecord("S-NJ-003",
                LocalDateTime.of(2026, 9, 5, 11, 0), BigDecimal.valueOf(250.0), null);
        rows.add(invalid);
        // 4) 传感器不存在 → 业务失败
        rows.add(buildRecord("S-XX-999", LocalDateTime.of(2026, 9, 5, 12, 0),
                BigDecimal.valueOf(66.0), null));

        importReq.setRecords(rows);
        importService.doImport(importReq);
    }

    private void createSensor(String code, String name, String location, double lng, double lat) {
        SensorCreateRequest req = new SensorCreateRequest();
        req.setSensorCode(code);
        req.setName(name);
        req.setLocation(location);
        req.setLongitude(BigDecimal.valueOf(lng));
        req.setLatitude(BigDecimal.valueOf(lat));
        sensorService.create(req);
    }

    private String createBatch(String sensorCode, LocalDateTime start, LocalDateTime end, String purpose) {
        BatchCreateRequest req = new BatchCreateRequest();
        req.setSensorCode(sensorCode);
        req.setStartTime(start);
        req.setEndTime(end);
        req.setPurpose(purpose);
        req.setOperator("system");
        return batchService.create(req).getBatchNo();
    }

    private RecordCreateRequest buildRecord(String sensorCode, LocalDateTime time, BigDecimal db,
                                            String batchNo) {
        RecordCreateRequest req = new RecordCreateRequest();
        req.setSensorCode(sensorCode);
        req.setBatchNo(batchNo);
        req.setSampleTime(time);
        req.setLongitude(BigDecimal.valueOf(118.77880));
        req.setLatitude(BigDecimal.valueOf(32.04170));
        req.setDbValue(db);
        req.setSpectrumSummary("{\"bands\":[{\"hz\":63,\"db\":" + db.subtract(BigDecimal.valueOf(10))
                + "},{\"hz\":125,\"db\":" + db.subtract(BigDecimal.valueOf(6))
                + "},{\"hz\":250,\"db\":" + db.subtract(BigDecimal.valueOf(3))
                + "}],\"unit\":\"dB(A)\"}");
        req.setRawDataHash(HashUtils.sha256("raw|" + sensorCode + "|" + time + "|" + db));
        return req;
    }

    private com.citynoise.evidence.dto.EvidenceCreateRequest evidenceReq(String uri, String note) {
        com.citynoise.evidence.dto.EvidenceCreateRequest req =
                new com.citynoise.evidence.dto.EvidenceCreateRequest();
        req.setFileUri(uri);
        req.setChangeNote(note);
        return req;
    }

    private void handle(Long anomalyId, String status, String note) {
        AnomalyHandleRequest req = new AnomalyHandleRequest();
        req.setStatus(status);
        req.setHandleNote(note);
        recordService.handleAnomaly(anomalyId, req);
    }
}

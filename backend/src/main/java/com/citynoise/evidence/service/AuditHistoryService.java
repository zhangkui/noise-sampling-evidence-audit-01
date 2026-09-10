package com.citynoise.evidence.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.citynoise.evidence.audit.AuditChainService;
import com.citynoise.evidence.audit.EvidenceChainService;
import com.citynoise.evidence.common.BusinessException;
import com.citynoise.evidence.common.ErrorCode;
import com.citynoise.evidence.dto.RecordHistoryVO;
import com.citynoise.evidence.entity.AuditLog;
import com.citynoise.evidence.entity.EvidenceVersion;
import com.citynoise.evidence.mapper.AuditLogMapper;
import com.citynoise.evidence.mapper.EvidenceVersionMapper;
import com.citynoise.evidence.mapper.NoiseRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 证据链回放：按记录聚合 证据版本 + 审计时间线，并重放哈希链校验完整性。
 */
@Service
@RequiredArgsConstructor
public class AuditHistoryService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final AuditLogMapper auditLogMapper;
    private final EvidenceVersionMapper evidenceMapper;
    private final NoiseRecordMapper recordMapper;
    private final AuditChainService auditChainService;
    private final EvidenceChainService evidenceChainService;

    public RecordHistoryVO replay(Long recordId) {
        if (recordMapper.selectById(recordId) == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }

        List<AuditLog> logs = auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getRecordId, recordId)
                .orderByAsc(AuditLog::getChainSeq));
        List<EvidenceVersion> versions = evidenceMapper.selectList(
                new LambdaQueryWrapper<EvidenceVersion>()
                        .eq(EvidenceVersion::getRecordId, recordId)
                        .orderByAsc(EvidenceVersion::getVersionNo));

        // 重放审计哈希链
        String auditBroken = auditChainService.verifyChain(recordId, logs);
        // 重放证据哈希链
        String evidenceBroken = evidenceChainService.verify(recordId, versions);
        String broken = auditBroken != null ? auditBroken
                : (evidenceBroken != null ? "证据链: " + evidenceBroken : null);

        RecordHistoryVO vo = new RecordHistoryVO();
        vo.setRecord(recordMapper.selectRecordById(recordId));
        vo.setChainValid(broken == null);
        vo.setBrokenAt(broken);
        vo.setEvidences(versions.stream().map(this::toEvidenceVO).toList());
        vo.setTimeline(logs.stream().map(this::toTimelineItem).toList());
        return vo;
    }

    /**
     * 审计日志分页查询（审计浏览页）。
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<AuditLog> pageAudit(
            long page, long size, Long recordId, String entityType, String action) {
        return auditLogMapper.selectPage(
                com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(page, size),
                new LambdaQueryWrapper<AuditLog>()
                        .eq(recordId != null, AuditLog::getRecordId, recordId)
                        .eq(entityType != null && !entityType.isBlank(),
                                AuditLog::getEntityType, entityType)
                        .eq(action != null && !action.isBlank(), AuditLog::getAction, action)
                        .orderByDesc(AuditLog::getId));
    }

    private RecordHistoryVO.EvidenceVersion toEvidenceVO(EvidenceVersion v) {
        RecordHistoryVO.EvidenceVersion e = new RecordHistoryVO.EvidenceVersion();
        e.setId(v.getId());
        e.setVersionNo(v.getVersionNo());
        e.setAlgorithm(v.getAlgorithm());
        e.setPrevHash(v.getPrevHash());
        e.setEvidenceHash(v.getEvidenceHash());
        e.setFileUri(v.getFileUri());
        e.setChangeNote(v.getChangeNote());
        e.setCreatedBy(v.getCreatedBy());
        e.setCreatedAt(v.getCreatedAt() == null ? null : FMT.format(v.getCreatedAt()));
        return e;
    }

    private RecordHistoryVO.AuditTimelineItem toTimelineItem(AuditLog l) {
        RecordHistoryVO.AuditTimelineItem t = new RecordHistoryVO.AuditTimelineItem();
        t.setId(l.getId());
        t.setChainSeq(l.getChainSeq());
        t.setEntityType(l.getEntityType());
        t.setEntityId(l.getEntityId());
        t.setAction(l.getAction());
        t.setBeforeJson(l.getBeforeJson());
        t.setAfterJson(l.getAfterJson());
        t.setOperator(l.getOperator());
        t.setPrevHash(l.getPrevHash());
        t.setEntryHash(l.getEntryHash());
        t.setCreatedAt(l.getCreatedAt() == null ? null : FMT.format(l.getCreatedAt()));
        return t;
    }
}

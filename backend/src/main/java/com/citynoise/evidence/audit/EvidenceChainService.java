package com.citynoise.evidence.audit;

import com.citynoise.evidence.common.BusinessException;
import com.citynoise.evidence.common.ErrorCode;
import com.citynoise.evidence.common.HashUtils;
import com.citynoise.evidence.entity.ChainHead;
import com.citynoise.evidence.entity.EvidenceVersion;
import com.citynoise.evidence.entity.NoiseRecord;
import com.citynoise.evidence.mapper.ChainHeadMapper;
import com.citynoise.evidence.mapper.EvidenceVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 证据版本哈希链：每条记录独立一条链（chain_key = ev:{recordId}），
 * 证据只能追加新版本，不能修改/删除。
 *
 * <p>加锁顺序固定为 审计链(rec:) → 证据链(ev:)，与记录创建流程保持一致，避免跨流程死锁。</p>
 */
@Service
@RequiredArgsConstructor
public class EvidenceChainService {

    public static final String EVIDENCE_CHAIN_PREFIX = "ev:";

    private final ChainHeadMapper chainHeadMapper;
    private final EvidenceVersionMapper evidenceMapper;
    private final AuditChainService auditChainService;

    /**
     * 追加证据版本。必须在业务事务内调用。
     */
    @Transactional(rollbackFor = Exception.class)
    public EvidenceVersion appendVersion(NoiseRecord record, String fileUri, String changeNote,
                                         String operator, String operatorIp, String traceId) {
        String recKey = AuditChainService.RECORD_CHAIN_PREFIX + record.getId();
        String evKey = EVIDENCE_CHAIN_PREFIX + record.getId();

        // 固定加锁顺序：先审计链后证据链
        lockHead(recKey);
        ChainHead evHead = lockHead(evKey);

        int nextVersion = evHead == null ? 1 : evHead.getLastSeq() + 1;
        String prevHash = evHead == null ? HashUtils.GENESIS : evHead.getLastHash();

        EvidenceVersion version = new EvidenceVersion();
        version.setRecordId(record.getId());
        version.setVersionNo(nextVersion);
        version.setAlgorithm("SHA-256");
        version.setPrevHash(prevHash);
        version.setFileUri(fileUri);
        version.setChangeNote(changeNote);
        version.setCreatedBy(operator);

        String payload = String.join("|",
                String.valueOf(nextVersion),
                String.valueOf(record.getId()),
                nullToEmpty(record.getRawDataHash()),
                nullToEmpty(fileUri),
                nullToEmpty(changeNote),
                "SHA-256",
                nullToEmpty(operator));
        version.setHashPayload(payload);
        version.setEvidenceHash(HashUtils.chainHash(prevHash, payload));
        evidenceMapper.insert(version);

        if (evHead == null) {
            ChainHead created = new ChainHead();
            created.setChainKey(evKey);
            created.setLastSeq(1);
            created.setLastHash(version.getEvidenceHash());
            chainHeadMapper.insert(created);
        } else {
            evHead.setLastSeq(nextVersion);
            evHead.setLastHash(version.getEvidenceHash());
            chainHeadMapper.updateById(evHead);
        }

        // 证据关联必须落审计日志（同一事务）
        auditChainService.append(operator, operatorIp, traceId, record.getId(),
                AuditConstants.ENTITY_EVIDENCE, "evidence:" + version.getId(),
                AuditConstants.ACTION_EVIDENCE_LINK, null, version);
        return version;
    }

    /**
     * 校验某条记录的证据链是否完整。
     */
    public String verify(Long recordId, List<EvidenceVersion> versions) {
        String expectedPrev = HashUtils.GENESIS;
        int expectedVersion = 1;
        for (EvidenceVersion v : versions) {
            if (v.getVersionNo() == null || v.getVersionNo() != expectedVersion) {
                return "证据版本号断裂: 期望 v" + expectedVersion;
            }
            if (!expectedPrev.equals(v.getPrevHash())) {
                return "证据前驱哈希不匹配: v" + expectedVersion;
            }
            String recomputed = HashUtils.chainHash(expectedPrev, v.getHashPayload());
            if (!recomputed.equals(v.getEvidenceHash())) {
                return "证据哈希被篡改: v" + expectedVersion;
            }
            expectedPrev = v.getEvidenceHash();
            expectedVersion++;
        }
        return null;
    }

    private ChainHead lockHead(String key) {
        ChainHead head = chainHeadMapper.selectForUpdate(key);
        if (head == null) {
            // 先插入占位链头再加行锁，确保并发下不会重复插入
            ChainHead placeholder = new ChainHead();
            placeholder.setChainKey(key);
            placeholder.setLastSeq(0);
            placeholder.setLastHash(HashUtils.GENESIS);
            try {
                chainHeadMapper.insert(placeholder);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发首插：另一事务已插入，行锁等待后重读
                return chainHeadMapper.selectForUpdate(key);
            }
            return chainHeadMapper.selectForUpdate(key);
        }
        return head;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

package com.citynoise.evidence.audit;

import com.citynoise.evidence.common.HashUtils;
import com.citynoise.evidence.entity.AuditLog;
import com.citynoise.evidence.entity.ChainHead;
import com.citynoise.evidence.mapper.AuditLogMapper;
import com.citynoise.evidence.mapper.ChainHeadMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

/**
 * 审计哈希链服务。
 *
 * <p>每条噪声记录拥有一条独立链（chain_key = rec:{recordId}）：记录创建、异常状态变更、
 * 处理说明、证据关联都按链内序号追加。entryHash = SHA-256(prevHash | 规范化负载)，
 * 任意历史条目被篡改都会导致后续重算不匹配。</p>
 *
 * <p>链头用 SELECT ... FOR UPDATE 行锁推进，调用方须在事务内；本方法以 REQUIRED 加入
 * 外层业务事务，保证业务变更与审计追加原子提交（审计不会与业务数据不一致）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditChainService {

    public static final String RECORD_CHAIN_PREFIX = "rec:";
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final ChainHeadMapper chainHeadMapper;
    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 向指定记录链追加审计条目。
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public AuditLog append(String operator, String operatorIp, String traceId, Long recordId,
                           String entityType, String entityId, String action,
                           Object before, Object after) {
        String chainKey = RECORD_CHAIN_PREFIX + (recordId == null ? 0L : recordId);
        String beforeJson = toJson(before);
        String afterJson = toJson(after);

        // 行锁串行化：同一记录链的并发追加在此排队
        ChainHead head = chainHeadMapper.selectForUpdate(chainKey);
        int nextSeq;
        String prevHash;
        if (head == null) {
            head = new ChainHead();
            head.setChainKey(chainKey);
            head.setLastSeq(0);
            head.setLastHash(HashUtils.GENESIS);
            chainHeadMapper.insert(head);
            nextSeq = 1;
            prevHash = HashUtils.GENESIS;
        } else {
            nextSeq = head.getLastSeq() + 1;
            prevHash = head.getLastHash();
        }

        AuditLog entry = new AuditLog();
        entry.setChainSeq(nextSeq);
        entry.setTraceId(traceId);
        entry.setRecordId(recordId);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setBeforeJson(beforeJson);
        entry.setAfterJson(afterJson);
        entry.setOperator(operator);
        entry.setOperatorIp(operatorIp);
        entry.setPrevHash(prevHash);

        String payload = canonicalPayload(nextSeq, recordId, entityType, entityId, action,
                beforeJson, afterJson, operator);
        String entryHash = HashUtils.chainHash(prevHash, payload);
        entry.setEntryHash(entryHash);
        entry.setHashPayload(payload);

        auditLogMapper.insert(entry);

        head.setLastSeq(nextSeq);
        head.setLastHash(entryHash);
        chainHeadMapper.updateById(head);
        return entry;
    }

    /**
     * 按记录链顺序回放，重算哈希校验完整性。
     *
     * @return null 表示链完整；否则返回第一个断裂位置说明
     */
    public String verifyChain(Long recordId, java.util.List<AuditLog> orderedEntries) {
        String expectedPrev = HashUtils.GENESIS;
        int expectedSeq = 1;
        for (AuditLog e : orderedEntries) {
            if (e.getChainSeq() == null || e.getChainSeq() != expectedSeq) {
                return "序号断裂: 期望 seq=" + expectedSeq + "，实际 seq=" + e.getChainSeq();
            }
            if (!expectedPrev.equals(e.getPrevHash())) {
                return "前驱哈希不匹配: seq=" + expectedSeq;
            }
            String payload = canonicalPayload(e.getChainSeq(), e.getRecordId(), e.getEntityType(),
                    e.getEntityId(), e.getAction(), e.getBeforeJson(), e.getAfterJson(), e.getOperator());
            String recomputed = HashUtils.chainHash(expectedPrev, payload);
            if (!recomputed.equals(e.getEntryHash())) {
                return "条目哈希被篡改: seq=" + expectedSeq;
            }
            expectedPrev = e.getEntryHash();
            expectedSeq++;
        }
        return null;
    }

    private String canonicalPayload(int seq, Long recordId, String entityType, String entityId,
                                    String action, String beforeJson, String afterJson, String operator) {
        // 分隔符拼接的确定性规范化字符串
        return String.join("|",
                String.valueOf(seq),
                recordId == null ? "" : String.valueOf(recordId),
                nullToEmpty(entityType),
                nullToEmpty(entityId),
                nullToEmpty(action),
                nullToEmpty(beforeJson),
                nullToEmpty(afterJson),
                nullToEmpty(operator));
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String toJson(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            log.warn("审计对象序列化失败", e);
            return String.valueOf(o);
        }
    }
}

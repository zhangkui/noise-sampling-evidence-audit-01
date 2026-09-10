package com.citynoise.evidence.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.citynoise.evidence.audit.AuditChainService;
import com.citynoise.evidence.audit.AuditConstants;
import com.citynoise.evidence.common.WebUtils;
import com.citynoise.evidence.dto.ImportItemResult;
import com.citynoise.evidence.dto.ImportResultVO;
import com.citynoise.evidence.dto.RecordCreateRequest;
import com.citynoise.evidence.dto.RecordImportRequest;
import com.citynoise.evidence.entity.ImportItem;
import com.citynoise.evidence.entity.ImportTask;
import com.citynoise.evidence.entity.NoiseRecord;
import com.citynoise.evidence.mapper.ImportItemMapper;
import com.citynoise.evidence.mapper.ImportTaskMapper;
import com.citynoise.evidence.security.SecurityUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 批量导入：
 * <ul>
 *   <li>逐行做 Bean 校验，校验失败的行返回具体字段原因，不影响其他行；</li>
 *   <li>每一行在独立事务中落库，失败行自动回滚、绝不写入；</li>
 *   <li>文件内自相重复的行：首行成功，后续行返回“重复提交”原因；</li>
 *   <li>任务与逐条结果持久化，供“批量导入结果”页面回看。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final NoiseRecordService recordService;
    private final ImportTaskMapper taskMapper;
    private final ImportItemMapper itemMapper;
    private final AuditChainService auditChainService;
    private final Validator validator;

    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO doImport(RecordImportRequest request) {
        List<RecordCreateRequest> rows = request.getRecords();
        List<ImportItemResult> results = new ArrayList<>(rows.size());

        int success = 0;
        int fail = 0;
        for (int i = 0; i < rows.size(); i++) {
            RecordCreateRequest row = rows.get(i);
            ImportItemResult result = new ImportItemResult();
            result.setRowIndex(i + 1);
            if (row != null) {
                result.setSensorCode(row.getSensorCode());
                result.setSampleTime(row.getSampleTime() == null ? null : row.getSampleTime().toString());
                result.setDbValue(row.getDbValue());
            }

            // 1. 逐行字段校验：失败原因精确到字段
            String violation = validateRow(row);
            if (violation != null) {
                result.setSuccess(false);
                result.setFailReason(violation);
                fail++;
                results.add(result);
                continue;
            }

            // 2. 独立事务落库：重复/业务错误只回滚这一行
            try {
                NoiseRecord record = recordService.createInNewTransaction(row);
                result.setSuccess(true);
                result.setRecordId(record.getId());
                result.setRecordNo(record.getRecordNo());
                success++;
            } catch (Exception e) {
                result.setSuccess(false);
                result.setFailReason(describeError(e));
                fail++;
            }
            results.add(result);
        }

        // 3. 持久化导入任务与逐条明细
        ImportTask task = new ImportTask();
        task.setImportNo("IMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        task.setFileName(request.getFileName());
        task.setTotalCount(rows.size());
        task.setSuccessCount(success);
        task.setFailCount(fail);
        task.setStatus(success == rows.size() ? "DONE" : (success == 0 ? "ALL_FAILED" : "PARTIAL"));
        task.setOperator(SecurityUtils.currentUsername());
        taskMapper.insert(task);

        List<ImportItem> items = new ArrayList<>(results.size());
        for (ImportItemResult r : results) {
            ImportItem item = new ImportItem();
            item.setTaskId(task.getId());
            item.setRowIndex(r.getRowIndex());
            item.setRecordNo(r.getRecordNo());
            item.setSensorCode(r.getSensorCode());
            item.setSampleTime(r.getSampleTime());
            item.setDbValue(r.getDbValue());
            item.setSuccess(Boolean.TRUE.equals(r.getSuccess()));
            item.setFailReason(r.getFailReason());
            items.add(item);
        }
        items.forEach(itemMapper::insert);

        // 4. 导入动作留痕（汇总信息挂全局链）
        auditChainService.append(SecurityUtils.currentUsername(), WebUtils.clientIp(),
                UUID.randomUUID().toString(), 0L,
                AuditConstants.ENTITY_IMPORT, task.getImportNo(),
                AuditConstants.ACTION_IMPORT, null,
                new ImportSummary(task.getImportNo(), task.getTotalCount(), success, fail, task.getStatus()));

        ImportResultVO vo = new ImportResultVO();
        vo.setTaskId(task.getId());
        vo.setImportNo(task.getImportNo());
        vo.setTotalCount(task.getTotalCount());
        vo.setSuccessCount(success);
        vo.setFailCount(fail);
        vo.setStatus(task.getStatus());
        vo.setItems(results);
        return vo;
    }

    public Page<ImportTask> pageTasks(long page, long size) {
        return taskMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<ImportTask>().orderByDesc(ImportTask::getId));
    }

    public ImportTask getTask(Long id) {
        ImportTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new com.citynoise.evidence.common.BusinessException(
                    com.citynoise.evidence.common.ErrorCode.NOT_FOUND, "导入任务不存在");
        }
        return task;
    }

    public List<ImportItem> listItems(Long taskId) {
        return itemMapper.selectList(new LambdaQueryWrapper<ImportItem>()
                .eq(ImportItem::getTaskId, taskId)
                .orderByAsc(ImportItem::getRowIndex));
    }

    private String validateRow(RecordCreateRequest row) {
        if (row == null) {
            return "行数据为空";
        }
        Set<ConstraintViolation<RecordCreateRequest>> violations = validator.validate(row);
        if (violations.isEmpty()) {
            return null;
        }
        return violations.stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining("; "));
    }

    private String describeError(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return e.getClass().getSimpleName();
        }
        // 业务异常信息本身已是面向用户的中文原因；其他异常截断防止过长
        return msg.length() > 480 ? msg.substring(0, 480) : msg;
    }

    /** 审计中使用的导入快照。 */
    public record ImportSummary(String importNo, int total, int success, int fail, String status) {
    }
}

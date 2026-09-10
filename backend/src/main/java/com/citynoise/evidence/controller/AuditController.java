package com.citynoise.evidence.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.citynoise.evidence.common.PageResult;
import com.citynoise.evidence.common.Result;
import com.citynoise.evidence.dto.RecordHistoryVO;
import com.citynoise.evidence.entity.AuditLog;
import com.citynoise.evidence.service.AuditHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditHistoryService historyService;

    /**
     * 按记录回放完整变更历史（证据版本 + 审计时间线 + 哈希链校验结果）。
     */
    @GetMapping("/records/{recordId}/history")
    public Result<RecordHistoryVO> replay(@PathVariable Long recordId) {
        return Result.ok(historyService.replay(recordId));
    }

    /**
     * 审计日志分页浏览。
     */
    @GetMapping("/logs")
    public Result<PageResult<AuditLog>> logs(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) Long recordId,
                                             @RequestParam(required = false) String entityType,
                                             @RequestParam(required = false) String action) {
        Page<AuditLog> p = historyService.pageAudit(page, size, recordId, entityType, action);
        return Result.ok(PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize()));
    }
}

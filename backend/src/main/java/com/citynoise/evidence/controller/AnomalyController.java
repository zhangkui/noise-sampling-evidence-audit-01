package com.citynoise.evidence.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.citynoise.evidence.common.PageResult;
import com.citynoise.evidence.common.Result;
import com.citynoise.evidence.dto.AnomalyHandleRequest;
import com.citynoise.evidence.entity.AnomalyEvent;
import com.citynoise.evidence.mapper.AnomalyEventMapper;
import com.citynoise.evidence.service.NoiseRecordService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final AnomalyEventMapper anomalyMapper;
    private final NoiseRecordService recordService;

    /**
     * 异常事件分页（可按状态筛选），供异常处理页面使用。
     */
    @GetMapping
    public Result<PageResult<AnomalyEvent>> page(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) String status) {
        Page<AnomalyEvent> p = anomalyMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<AnomalyEvent>()
                        .eq(status != null && !status.isBlank(), AnomalyEvent::getStatus, status)
                        .orderByDesc(AnomalyEvent::getId));
        return Result.ok(PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize()));
    }

    /**
     * 异常处理：状态变更 + 处理说明（落审计哈希链）。
     */
    @PostMapping("/{id}/handle")
    public Result<AnomalyEvent> handle(@PathVariable Long id,
                                       @Valid @RequestBody AnomalyHandleRequest request) {
        return Result.ok(recordService.handleAnomaly(id, request));
    }
}

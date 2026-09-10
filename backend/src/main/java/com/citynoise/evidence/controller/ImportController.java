package com.citynoise.evidence.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.citynoise.evidence.common.PageResult;
import com.citynoise.evidence.common.Result;
import com.citynoise.evidence.dto.ImportResultVO;
import com.citynoise.evidence.dto.RecordImportRequest;
import com.citynoise.evidence.entity.ImportItem;
import com.citynoise.evidence.entity.ImportTask;
import com.citynoise.evidence.service.ImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    /**
     * 批量导入：逐行返回成功/失败原因，失败行不写库。
     */
    @PostMapping
    public Result<ImportResultVO> doImport(@Valid @RequestBody RecordImportRequest request) {
        return Result.ok(importService.doImport(request));
    }

    @GetMapping
    public Result<PageResult<ImportTask>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        Page<ImportTask> p = importService.pageTasks(page, size);
        return Result.ok(PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize()));
    }

    @GetMapping("/{id}")
    public Result<ImportTask> detail(@PathVariable Long id) {
        return Result.ok(importService.getTask(id));
    }

    @GetMapping("/{id}/items")
    public Result<List<ImportItem>> items(@PathVariable Long id) {
        importService.getTask(id);
        return Result.ok(importService.listItems(id));
    }
}

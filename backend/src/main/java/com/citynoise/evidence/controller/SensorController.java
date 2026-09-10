package com.citynoise.evidence.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.citynoise.evidence.common.PageResult;
import com.citynoise.evidence.common.Result;
import com.citynoise.evidence.dto.SensorCreateRequest;
import com.citynoise.evidence.entity.Sensor;
import com.citynoise.evidence.service.SensorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sensors")
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;

    @GetMapping
    public Result<List<Sensor>> list() {
        return Result.ok(sensorService.listAll());
    }

    @GetMapping("/page")
    public Result<PageResult<Sensor>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String status) {
        Page<Sensor> p = sensorService.page(page, size, keyword, status);
        return Result.ok(PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize()));
    }

    @PostMapping
    public Result<Sensor> create(@Valid @RequestBody SensorCreateRequest request) {
        return Result.ok(sensorService.create(request));
    }
}

package com.citynoise.evidence.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.citynoise.evidence.common.PageResult;
import com.citynoise.evidence.common.Result;
import com.citynoise.evidence.dto.RecordCreateRequest;
import com.citynoise.evidence.dto.RecordQuery;
import com.citynoise.evidence.dto.RecordTrendPoint;
import com.citynoise.evidence.dto.RecordVO;
import com.citynoise.evidence.entity.AnomalyEvent;
import com.citynoise.evidence.entity.EvidenceVersion;
import com.citynoise.evidence.entity.NoiseRecord;
import com.citynoise.evidence.service.NoiseRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class NoiseRecordController {

    private final NoiseRecordService recordService;

    /**
     * 分页 + 时间范围 + 传感器编号 + 异常状态 + 分贝区间筛选。
     */
    @GetMapping
    public Result<PageResult<RecordVO>> page(@ModelAttribute RecordQuery query) {
        Page<RecordVO> p = recordService.pageRecords(query);
        return Result.ok(PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize()));
    }

    @GetMapping("/{id}")
    public Result<RecordVO> detail(@PathVariable Long id) {
        return Result.ok(recordService.getDetail(id));
    }

    @PostMapping
    public Result<NoiseRecord> create(@Valid @RequestBody RecordCreateRequest request) {
        return Result.ok(recordService.create(request));
    }

    @GetMapping("/{id}/anomalies")
    public Result<List<AnomalyEvent>> anomalies(@PathVariable Long id) {
        recordService.getEntity(id);
        return Result.ok(recordService.listAnomalies(id));
    }

    @GetMapping("/{id}/evidences")
    public Result<List<EvidenceVersion>> evidences(@PathVariable Long id) {
        recordService.getEntity(id);
        return Result.ok(recordService.listEvidences(id));
    }

    /**
     * 分贝趋势（按小时聚合）。
     */
    @GetMapping("/trend/chart")
    public Result<List<RecordTrendPoint>> trend(@RequestParam(required = false) String sensorCode,
                                                @RequestParam(required = false)
                                                    LocalDateTime startTime,
                                                @RequestParam(required = false)
                                                    LocalDateTime endTime) {
        return Result.ok(recordService.trend(sensorCode, startTime, endTime));
    }
}

package com.citynoise.evidence.controller;

import com.citynoise.evidence.common.BusinessException;
import com.citynoise.evidence.common.ErrorCode;
import com.citynoise.evidence.common.Result;
import com.citynoise.evidence.dto.BatchCreateRequest;
import com.citynoise.evidence.entity.SamplingBatch;
import com.citynoise.evidence.service.BatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    @GetMapping
    public Result<List<SamplingBatch>> list(@RequestParam(required = false) String sensorCode) {
        return Result.ok(batchService.list(sensorCode));
    }

    @PostMapping
    public Result<SamplingBatch> create(@Valid @RequestBody BatchCreateRequest request) {
        return Result.ok(batchService.create(request));
    }

    /**
     * 关闭批次：仅 ACTIVE → CLOSED；重复关闭幂等返回当前状态，不重复写审计。
     */
    @PostMapping("/{id}/close")
    public Result<SamplingBatch> close(@PathVariable Long id) {
        return Result.ok(batchService.close(id));
    }

    /**
     * 时间区间重叠检测：查询同一传感器在给定区间内是否存在重叠采样批次。
     */
    @GetMapping("/overlap-check")
    public Result<Map<String, Object>> overlapCheck(@RequestParam String sensorCode,
                                                    @RequestParam LocalDateTime startTime,
                                                    @RequestParam LocalDateTime endTime,
                                                    @RequestParam(required = false) Long excludeId) {
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
        List<SamplingBatch> overlaps =
                batchService.findOverlaps(sensorCode, startTime, endTime, excludeId);
        return Result.ok(Map.of(
                "overlap", !overlaps.isEmpty(),
                "conflicts", overlaps));
    }
}

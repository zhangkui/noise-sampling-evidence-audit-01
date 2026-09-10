package com.citynoise.evidence.controller;

import com.citynoise.evidence.common.Result;
import com.citynoise.evidence.dto.EvidenceCreateRequest;
import com.citynoise.evidence.entity.EvidenceVersion;
import com.citynoise.evidence.service.NoiseRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/records/{recordId}/evidences")
@RequiredArgsConstructor
public class EvidenceController {

    private final NoiseRecordService recordService;

    /**
     * 关联/追加证据版本（证据只增不改，自动续接哈希链并写审计）。
     */
    @PostMapping
    public Result<EvidenceVersion> append(@PathVariable Long recordId,
                                          @Valid @RequestBody EvidenceCreateRequest request) {
        return Result.ok(recordService.linkEvidence(recordId, request));
    }
}

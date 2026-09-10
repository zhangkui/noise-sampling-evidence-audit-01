package com.citynoise.evidence.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.citynoise.evidence.audit.AuditChainService;
import com.citynoise.evidence.audit.AuditConstants;
import com.citynoise.evidence.common.BusinessException;
import com.citynoise.evidence.common.ErrorCode;
import com.citynoise.evidence.common.WebUtils;
import com.citynoise.evidence.dto.SensorCreateRequest;
import com.citynoise.evidence.entity.Sensor;
import com.citynoise.evidence.mapper.SensorMapper;
import com.citynoise.evidence.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SensorService {

    private final SensorMapper sensorMapper;
    private final AuditChainService auditChainService;

    public List<Sensor> listAll() {
        return sensorMapper.selectList(new LambdaQueryWrapper<Sensor>()
                .orderByAsc(Sensor::getSensorCode));
    }

    public Page<Sensor> page(long page, long size, String keyword, String status) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        LambdaQueryWrapper<Sensor> wrapper = new LambdaQueryWrapper<Sensor>()
                .and(hasKeyword, w -> w.like(Sensor::getSensorCode, keyword)
                        .or().like(Sensor::getName, keyword))
                .eq(status != null && !status.isBlank(), Sensor::getStatus, status)
                .orderByAsc(Sensor::getSensorCode);
        return sensorMapper.selectPage(Page.of(page, size), wrapper);
    }

    public Sensor getByCode(String sensorCode) {
        Sensor sensor = sensorMapper.selectOne(new LambdaQueryWrapper<Sensor>()
                .eq(Sensor::getSensorCode, sensorCode));
        if (sensor == null) {
            throw new BusinessException(ErrorCode.SENSOR_NOT_FOUND);
        }
        return sensor;
    }

    @Transactional(rollbackFor = Exception.class)
    public Sensor create(SensorCreateRequest request) {
        Sensor sensor = new Sensor();
        sensor.setSensorCode(request.getSensorCode());
        sensor.setName(request.getName());
        sensor.setLocation(request.getLocation());
        sensor.setLongitude(request.getLongitude());
        sensor.setLatitude(request.getLatitude());
        sensor.setStatus("ONLINE");
        try {
            sensorMapper.insert(sensor);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_RECORD,
                    "传感器编号已存在: " + request.getSensorCode());
        }
        // 传感器变更同样留痕（挂在全局链 record_id=0 上）
        auditChainService.append(SecurityUtils.currentUsername(), WebUtils.clientIp(),
                java.util.UUID.randomUUID().toString(), 0L,
                AuditConstants.ENTITY_SENSOR, sensor.getSensorCode(),
                AuditConstants.ACTION_SENSOR_CREATE, null, sensor);
        return sensor;
    }
}

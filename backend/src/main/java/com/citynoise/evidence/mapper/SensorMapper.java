package com.citynoise.evidence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.citynoise.evidence.entity.Sensor;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SensorMapper extends BaseMapper<Sensor> {
}

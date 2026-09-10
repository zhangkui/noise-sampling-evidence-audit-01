package com.citynoise.evidence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.citynoise.evidence.entity.AnomalyEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnomalyEventMapper extends BaseMapper<AnomalyEvent> {
}

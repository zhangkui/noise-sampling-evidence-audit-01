package com.citynoise.evidence.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.citynoise.evidence.dto.RecordQuery;
import com.citynoise.evidence.dto.RecordTrendPoint;
import com.citynoise.evidence.dto.RecordVO;
import com.citynoise.evidence.entity.NoiseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NoiseRecordMapper extends BaseMapper<NoiseRecord> {

    /**
     * 多条件分页查询（携带异常状态）。
     */
    IPage<RecordVO> selectRecordPage(IPage<RecordVO> page, @Param("q") RecordQuery query);

    /**
     * 单条记录详情（携带异常状态与批次号）。
     */
    RecordVO selectRecordById(@Param("id") Long id);

    /**
     * 分贝趋势：按小时聚合平均/最大分贝，仅统计未删除记录。
     */
    List<RecordTrendPoint> selectTrend(@Param("sensorCode") String sensorCode,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);
}

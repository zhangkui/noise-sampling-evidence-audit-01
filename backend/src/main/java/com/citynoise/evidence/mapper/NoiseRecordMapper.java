package com.citynoise.evidence.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.citynoise.evidence.dto.AnomalyStatusCountVO;
import com.citynoise.evidence.dto.BatchStatisticsVO;
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

    /**
     * 批次记录维度聚合：总数、首末采样时间、平均/最大/最小分贝。
     * 严格按 batch_id 过滤，只统计未删除记录；空批次返回各统计列为 null、COUNT 为 0 的一行。
     * 数据库端聚合，不把记录载入内存，可应对大批量记录。
     */
    BatchStatisticsVO selectBatchRecordStats(@Param("batchId") Long batchId);

    /**
     * 批次异常状态分布：仅关联本批次未删除记录的异常事件，按状态分组计数。
     * 无异常时返回空列表（由服务层补零）。
     */
    List<AnomalyStatusCountVO> selectBatchAnomalyStats(@Param("batchId") Long batchId);
}

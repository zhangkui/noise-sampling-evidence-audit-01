package com.citynoise.evidence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.citynoise.evidence.entity.SamplingBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SamplingBatchMapper extends BaseMapper<SamplingBatch> {

    /**
     * 查找同一传感器与给定时间区间重叠、且未被逻辑删除的批次（可排除自身）。
     * 重叠条件：existing.start &lt; end AND existing.end &gt; start（相邻边界不算冲突）。
     */
    @Select("""
            SELECT * FROM sampling_batch
            WHERE deleted = 0
              AND sensor_code = #{sensorCode}
              AND start_time < #{endTime}
              AND end_time > #{startTime}
              AND (#{excludeId} IS NULL OR id != #{excludeId})
            ORDER BY start_time
            """)
    List<SamplingBatch> findOverlapping(@Param("sensorCode") String sensorCode,
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime,
                                        @Param("excludeId") Long excludeId);

    /**
     * 仅当批次处于 ACTIVE 时置为 CLOSED（数据库层条件更新兜底，
     * 并发下最多一个事务生效，保证关闭操作幂等）。
     *
     * @return 受影响行数：1 表示本次关闭生效；0 表示已是 CLOSED
     */
    @Update("UPDATE sampling_batch SET status = 'CLOSED' WHERE id = #{id} AND status = 'ACTIVE' AND deleted = 0")
    int closeIfActive(@Param("id") Long id);
}

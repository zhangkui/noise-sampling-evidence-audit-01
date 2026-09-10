package com.citynoise.evidence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.citynoise.evidence.entity.ChainHead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChainHeadMapper extends BaseMapper<ChainHead> {

    /**
     * 行锁读取链头，必须在事务内调用。
     * 同一 chain_key 上的行锁保证并发事务严格串行推进序号与前驱哈希。
     */
    @Select("SELECT * FROM audit_chain WHERE chain_key = #{chainKey} FOR UPDATE")
    ChainHead selectForUpdate(@Param("chainKey") String chainKey);
}

package com.citynoise.evidence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 噪声采样记录。创建后业务字段不可修改（系统不提供更新接口）。
 */
@Data
@TableName("noise_record")
public class NoiseRecord {

    @TableId
    private Long id;

    private String recordNo;
    private String sensorCode;
    private Long batchId;
    private LocalDateTime sampleTime;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal dbValue;
    private String spectrumSummary;
    private String rawDataHash;
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

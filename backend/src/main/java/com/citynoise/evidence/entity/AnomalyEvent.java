package com.citynoise.evidence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("anomaly_event")
public class AnomalyEvent {

    @TableId
    private Long id;

    private String eventNo;
    private Long recordId;
    private String anomalyType;
    private BigDecimal thresholdValue;
    private BigDecimal dbValue;
    /** OPEN / PROCESSING / RESOLVED / IGNORED */
    private String status;
    private String description;
    private String handleNote;
    private String handledBy;
    private LocalDateTime handledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

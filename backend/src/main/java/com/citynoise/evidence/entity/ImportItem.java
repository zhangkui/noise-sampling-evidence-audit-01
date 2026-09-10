package com.citynoise.evidence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("import_item")
public class ImportItem {

    @TableId
    private Long id;

    private Long taskId;
    private Integer rowIndex;
    private String recordNo;
    private String sensorCode;
    private String sampleTime;
    private BigDecimal dbValue;
    private Boolean success;
    private String failReason;
    private LocalDateTime createdAt;
}

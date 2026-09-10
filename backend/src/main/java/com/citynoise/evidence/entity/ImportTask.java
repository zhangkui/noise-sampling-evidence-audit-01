package com.citynoise.evidence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("import_task")
public class ImportTask {

    @TableId
    private Long id;

    private String importNo;
    private String fileName;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private String status;
    private String operator;
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}

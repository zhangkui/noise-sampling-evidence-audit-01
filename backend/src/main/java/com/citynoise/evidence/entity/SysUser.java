package com.citynoise.evidence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId
    private Long id;

    private String username;
    private String password;
    private String realName;
    private String role;
    private Integer enabled;
    private LocalDateTime createdAt;
}

package com.bread.networkfilesystem.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
import com.bread.networkfilesystem.enums.OperationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @TableName sys_log
 */
@TableName(value ="sys_log")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SysLog implements Serializable {
    /**
     * 
     */
    @TableId(value = "log_id")
    private Long logId;

    /**
     * 
     */
    @TableField(value = "user_id")
    private Long userId;

    @TableField(value = "file_id")
    private Long fileId;

    /**
     * 冗余存储，方便不查用户表直接看日志
     */
    @TableField(value = "username")
    private String username;

    /**
     * 
     */
    @EnumValue
    @TableField(value = "operation")
    private OperationType operation;

    /**
     * 
     */
    @TableField(value = "details")
    private String details;

    @TableField(value = "client_ip")
    private String clientIp;

    /**
     * 
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;
}
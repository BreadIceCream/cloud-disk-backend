package com.bread.networkfilesystem.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
import com.bread.networkfilesystem.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @TableName sys_user_file
 */
@TableName(value ="sys_user_file")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysUserFile implements Serializable {
    /**
     * 
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     *
     */
    @TableField(value = "file_id")
    private Long fileId;

    /**
     * 用户权限角色
     */
    @EnumValue
    @TableField(value = "role")
    private UserRole role;

    /**
     * 
     */
    @TableField(value = "file_name")
    private String fileName;

    /**
     * 父目录的file_id
     */
    @TableField(value = "parent_file_id")
    private Long parentFileId;

    /**
     * 
     */
    @TableField(value = "is_folder")
    private Boolean isFolder;

    /**
     * 
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    /**
     * 
     */
    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
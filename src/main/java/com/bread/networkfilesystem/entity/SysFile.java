package com.bread.networkfilesystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

/**
 * 
 * @TableName sys_file
 */
@TableName(value ="sys_file")
@Data
public class SysFile implements Serializable {
    /**
     * 
     */
    @TableId(value = "file_id")
    private Long fileId;

    /**
     * 
     */
    @TableField(value = "real_path")
    private String realPath;

    /**
     * 
     */
    @TableField(value = "file_size")
    private Long fileSize;

    /**
     * 
     */
    @TableField(value = "file_type")
    private String fileType;

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
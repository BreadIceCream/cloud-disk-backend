package com.bread.networkfilesystem.vo;

import com.bread.networkfilesystem.enums.UserRole;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserFileVO {

    // sys_user_file 表的主键 (操作入口)
    private Long id;
    // sys_file 表的主键 (下载入口)
    private Long fileId;
    private String fileName;
    private Boolean isFolder;
    // 来自 sys_file 表
    private Long fileSize;
    // 来自 sys_file 表 (如果是文件夹，此字段通常为 null)
    private String fileType;
    // 权限角色
    private UserRole role;
    private LocalDateTime updatedAt;

}

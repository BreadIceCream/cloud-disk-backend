package com.bread.networkfilesystem.dto;

import com.bread.networkfilesystem.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "共享请求参数")
public class ShareRequestDto {

    @Schema(description = "针对哪个文件进行操作")
    private Long userFileId;
    @Schema(description = "目标用户名")
    private String targetUsername;
    @Schema(description = "目标用户ID")
    private Long targetUserId;
    @Schema(description = "赋予的权限 (EDITOR / VIEWER)")
    private UserRole targetRole;

}

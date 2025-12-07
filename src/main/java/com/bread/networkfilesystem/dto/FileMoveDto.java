package com.bread.networkfilesystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文件移动参数")
public class FileMoveDto {

    @Schema(description = "用户文件ID")
    private Long userFileId;
    @Schema(description = "目标文件夹ID")
    private Long targetParentFileId;

}

package com.bread.networkfilesystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "重命名文件参数")
public class RenameDto {

    @Schema(description = "用户文件ID")
    private Long userFileId;
    @Schema(description = "新文件名")
    private String newName;

}

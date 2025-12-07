package com.bread.networkfilesystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建文件夹参数")
public class FolderCreateDto {

    @Schema(description = "文件夹名称")
    private String folderName;
    @Schema(description = "父目录fileId", defaultValue = "0")
    private Long parentFileId;

}

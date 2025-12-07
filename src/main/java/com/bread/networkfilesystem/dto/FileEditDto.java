package com.bread.networkfilesystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文件编辑参数")
public class FileEditDto {

    @Schema(description = "用户文件ID")
    private Long userFileId;
    @Schema(description = "修改后的文件内容。全量内容")
    private String content;

}

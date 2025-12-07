package com.bread.networkfilesystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文件锁")
public class FileLockDto {

    @Schema(description = "用户文件ID")
    private Long userFileId;

}

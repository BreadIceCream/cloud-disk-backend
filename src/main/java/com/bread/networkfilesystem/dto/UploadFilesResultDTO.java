package com.bread.networkfilesystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UploadFilesResultDTO {

    private int total;
    private int successCount;
    private List<String> successFiles;
    private List<String> failedFiles;

}

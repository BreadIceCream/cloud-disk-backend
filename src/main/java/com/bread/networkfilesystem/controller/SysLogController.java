package com.bread.networkfilesystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bread.networkfilesystem.dto.Result;
import com.bread.networkfilesystem.entity.SysLog;
import com.bread.networkfilesystem.enums.OperationType;
import com.bread.networkfilesystem.service.SysLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/log")
@Tag(name = "系统日志")
public class SysLogController {

    @Autowired
    private SysLogService logService;

    @GetMapping("/user")
    @Operation(summary = "获取用户操作日志")
    public Result getUserLogs(@RequestAttribute("userId") Long userId,
                              @RequestParam(required = false) OperationType type,
                              @RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "30") Integer pageSize) {
        Page<SysLog> userLogs = logService.getUserLogs(userId, type, pageNum, pageSize);
        return Result.success(userLogs);
    }


    @GetMapping("/file")
    @Operation(summary = "获取文件操作日志")
    public Result getFileLogs(@RequestParam Long userFileId,
                              @RequestParam(required = false) OperationType type,
                              @RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "30") Integer pageSize,
                              @RequestAttribute("userId") Long userId) {
        Page<SysLog> fileLogs = logService.getFileLogs(userFileId, type, userId, pageNum, pageSize);
        return Result.success(fileLogs);
    }

}

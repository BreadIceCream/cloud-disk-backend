package com.bread.networkfilesystem.service.impl;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bread.networkfilesystem.annotations.RequireFileRole;
import com.bread.networkfilesystem.entity.SysLog;
import com.bread.networkfilesystem.entity.SysUserFile;
import com.bread.networkfilesystem.enums.OperationType;
import com.bread.networkfilesystem.enums.UserRole;
import com.bread.networkfilesystem.service.SysLogService;
import com.bread.networkfilesystem.mapper.SysLogMapper;
import com.bread.networkfilesystem.service.SysUserFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
* @author huang
* @description 针对表【sys_log】的数据库操作Service实现
* @createDate 2025-12-03 15:11:02
*/
@Service
@Slf4j
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLog> implements SysLogService{

    @Autowired
    private SysUserFileService sysUserFileService;

    @Override
    public Page<SysLog> getUserLogs(Long userId, OperationType type, Integer pageNum, Integer pageSize) {
        log.info("Get user logs: userId {}, type {}, pageNum {}, pageSize {}", userId, type, pageNum, pageSize);
        Assert.notNull(userId, "用户 ID 不能为空");
        // 查询用户操作日志，按照时间倒序排序
        return lambdaQuery()
                .eq(SysLog::getUserId, userId)
                .eq(type != null, SysLog::getOperation, type)
                .orderByDesc(SysLog::getCreatedAt)
                .page(Page.of(pageNum, pageSize));
    }

    @Override
    @RequireFileRole(UserRole.VIEWER)
    public Page<SysLog> getFileLogs(Long userFileId, OperationType type, Long userId, Integer pageNum, Integer pageSize) {
        log.info("Get file logs: userFileId {}, type {}, userId {}, pageNum {}, pageSize {}", userFileId, type, userId, pageNum, pageSize);
        SysUserFile userFile = sysUserFileService.getById(userFileId);
        // 查询该 file_id 的所有日志
        return lambdaQuery()
                .eq(SysLog::getFileId, userFile.getFileId())
                .eq(type != null, SysLog::getOperation, type)
                .orderByDesc(SysLog::getCreatedAt)
                .page(Page.of(pageNum, pageSize));
    }
}





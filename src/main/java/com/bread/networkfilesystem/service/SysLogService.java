package com.bread.networkfilesystem.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bread.networkfilesystem.entity.SysLog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.bread.networkfilesystem.enums.OperationType;

/**
* @author huang
* @description 针对表【sys_log】的数据库操作Service
* @createDate 2025-12-03 15:11:02
*/
public interface SysLogService extends IService<SysLog> {


    /**
     * 分页获取用户操作日志。可筛选操作类型
     * @param userId
     * @param type
     * @param pageNum
     * @param pageSize
     * @return
     */
    Page<SysLog> getUserLogs(Long userId, OperationType type, Integer pageNum, Integer pageSize);

    /**
     * 分页获取文件操作日志。可筛选操作类型
     * @param userFileId
     * @param type
     * @param userId
     * @param pageNum
     * @param pageSize
     * @return
     */
    Page<SysLog> getFileLogs(Long userFileId, OperationType type, Long userId, Integer pageNum, Integer pageSize);

}

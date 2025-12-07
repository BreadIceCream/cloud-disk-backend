package com.bread.networkfilesystem.service;

import com.bread.networkfilesystem.dto.UserDTO;
import com.bread.networkfilesystem.entity.SysUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.bread.networkfilesystem.exception.BusinessException;

/**
* @author huang
* @description 针对表【sys_user】的数据库操作Service
* @createDate 2025-12-03 15:11:02
*/
public interface SysUserService extends IService<SysUser> {

    /**
     * 用户注册
     * @param dto 注册参数（用户名、密码）
     * @throws BusinessException 如果用户名已存在
     */
    void register(UserDTO dto);

    /**
     * 用户登录
     * @param dto 登录参数
     * @return JWT Token 字符串
     * @throws BusinessException 如果账号或密码错误
     */
    String login(UserDTO dto);

    /**
     * 获取用户信息（脱敏）
     * @param userId 用户ID
     * @return 用户实体
     */
    SysUser getUserInfo(Long userId);


}

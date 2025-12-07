package com.bread.networkfilesystem.mapper;

import com.bread.networkfilesystem.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
* @author huang
* @description 针对表【sys_user】的数据库操作Mapper
* @createDate 2025-12-03 15:11:02
* @Entity com.bread.networkfilesystem.entity.SysUser
*/
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 更新用户已用空间
     * @param userId
     * @param size
     */
    @Update("update sys_user set used_space = used_space + #{size} where user_id = #{userId}")
    void updateUsedSpace(Long userId, long size);
}





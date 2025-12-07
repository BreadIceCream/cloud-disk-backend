package com.bread.networkfilesystem.mapper;

import com.bread.networkfilesystem.entity.SysUserFile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bread.networkfilesystem.vo.UserFileVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
* @author huang
* @description 针对表【sys_user_file】的数据库操作Mapper
* @createDate 2025-12-03 15:11:02
* @Entity com.bread.networkfilesystem.entity.SysUserFile
*/
@Mapper
public interface SysUserFileMapper extends BaseMapper<SysUserFile> {

    /**
     * 查询用户文件列表
     * @param userId
     * @param parentFileId
     * @return List<UserFileVO> 还未排序
     */
    List<UserFileVO> selectUserFileList(Long userId, Long parentFileId);

    /**
     * 根据文件名模糊搜索
     * @param userId
     * @param keyword
     * @return List<UserFileVO> 还未排序
     */
    List<UserFileVO> searchByKeyword(Long userId, String keyword);

}





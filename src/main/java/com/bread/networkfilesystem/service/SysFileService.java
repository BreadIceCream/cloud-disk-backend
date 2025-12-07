package com.bread.networkfilesystem.service;

import com.bread.networkfilesystem.dto.ShareRequestDto;
import com.bread.networkfilesystem.dto.UploadFilesResultDTO;
import com.bread.networkfilesystem.entity.SysFile;
import com.baomidou.mybatisplus.extension.service.IService;
import com.bread.networkfilesystem.vo.ShareListVO;
import com.bread.networkfilesystem.vo.UserFileVO;
import org.springframework.data.util.Pair;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

/**
* @author huang
* @description 针对表【sys_file】的数据库操作Service
* @createDate 2025-12-03 15:11:02
*/
public interface SysFileService extends IService<SysFile> {

    String STORAGE_ROOT = System.getProperty("user.dir") + File.separator + "netdisk_storage";

    /**
     * 批量并发上传文件
     *
     * @param files    文件列表
     * @param parentFileId 父目录ID
     * @param userId   当前用户ID
     * @param username 当前用户名
     * @return 上传结果汇总（成功/失败详情）
     */
    UploadFilesResultDTO uploadFiles(List<MultipartFile> files, Long parentFileId, Long userId, String username);

    /**
     * 创建文件夹
     * @param folderName
     * @param parentFileId
     * @param userId
     * @param username
     */
    void createFolder(String folderName, Long parentFileId, Long userId, String username);

    /**
     * 获取文件列表
     * @param userId
     * @param parentFileId
     * @return 文件列表，按照文件夹优先、修改时间倒序排序。若没有返回空集合
     */
    List<UserFileVO> getFileList(Long userId, Long parentFileId);

    /**
     * 搜索文件
     * @param userId
     * @param keyword
     * @return 文件列表，按照文件夹优先、修改时间倒序排序。若没有返回空集合（keyword为空也返回空集合）
     */
    List<UserFileVO> searchFile(Long userId, String keyword);

    /**
     * 删除用户文件
     * @param userFileId
     * @param userId
     * @param username
     */
    void deleteUserFile(Long userFileId, Long userId, String username);

    /**
     * 文件下载
     * 需要VIEWER权限
     * @param userFileId
     * @param userId
     * @param username
     * @param action download | preview
     * @return
     */
    Pair<SysFile, String> getFileForDownload(Long userFileId, Long userId, String username, String action);

    /**
     * 重命名用户文件
     * @param userFileId
     * @param newName
     * @param userId
     * @param username
     */
    void renameUserFile(Long userFileId, String newName, Long userId, String username);

    /**
     * 移动用户文件
     * @param userFileId
     * @param targetParentFileId
     * @param userId
     * @param username
     */
    void moveUserFile(Long userFileId, Long targetParentFileId, Long userId, String username);

    /**
     * 发起共享
     * @param userFileId 单独提取，供 AOP 拦截鉴权
     * @param req 其他参数
     * @param userId 当前用户ID (AOP 必需)
     * @param username 当前用户名
     */
    void shareFile(Long userFileId, ShareRequestDto req, Long userId, String username);

    /**
     * 撤销共享
     *
     * @param userFileId
     * @param targetUserId
     * @param targetUsername
     * @param userId
     * @param username
     */
    void revokeShare(Long userFileId, Long targetUserId, String targetUsername, Long userId, String username);

    /**
     * 更新共享权限
     * @param userFileId
     * @param dto
     * @param userId
     * @param username
     */
    void updateShareRole(Long userFileId, ShareRequestDto dto, Long userId, String username);

    /**
     * 获取共享成员列表
     * @param userFileId
     * @param userId
     * @param username
     * @return 共享成员列表，包含自己。按照权限降序排序。若没有返回空集合
     */
    List<ShareListVO> getShareList(Long userFileId, Long userId, String username);

    /**
     * 获取文件内容 (纯预览，不加锁)
     * @param userFileId
     * @param userId
     * @param username
     * @return
     */
    String getFileContent(Long userFileId, Long userId, String username);

    /**
     * 尝试对文件加锁 (进入编辑模式 / 心跳续期)
     * @param userFileId
     * @param userId
     * @return true=加锁/续期成功, false=已被他人锁定
     */
    boolean lockFile(Long userFileId, Long userId);

    /**
     * 对文件解锁 (退出编辑模式)
     * @param userFileId
     * @param userId
     * @return true=解锁成功, false=解锁失败（未持有锁）
     */
    boolean unlockFile(Long userFileId, Long userId);

    /**
     * 保存文件内容(需持有锁)
     *
     * @param userFileId
     * @param content
     * @param userId
     * @param username
     * @return 保存后的文件大小，单位byte
     */
    long saveFileContent(Long userFileId, String content, Long userId, String username);


}

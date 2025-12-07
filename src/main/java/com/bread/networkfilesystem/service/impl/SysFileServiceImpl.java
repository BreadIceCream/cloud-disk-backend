package com.bread.networkfilesystem.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bread.networkfilesystem.annotations.RequireFileRole;
import com.bread.networkfilesystem.constant.Constants;
import com.bread.networkfilesystem.dto.ShareRequestDto;
import com.bread.networkfilesystem.dto.UploadFilesResultDTO;
import com.bread.networkfilesystem.entity.SysFile;
import com.bread.networkfilesystem.entity.SysUser;
import com.bread.networkfilesystem.entity.SysUserFile;
import com.bread.networkfilesystem.enums.OperationType;
import com.bread.networkfilesystem.enums.UserRole;
import com.bread.networkfilesystem.exception.BusinessException;
import com.bread.networkfilesystem.mapper.SysUserFileMapper;
import com.bread.networkfilesystem.mapper.SysUserMapper;
import com.bread.networkfilesystem.mq.AuditLogProducer;
import com.bread.networkfilesystem.service.SysFileService;
import com.bread.networkfilesystem.mapper.SysFileMapper;
import com.bread.networkfilesystem.vo.ShareListVO;
import com.bread.networkfilesystem.vo.UserFileVO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.util.Pair;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
* @author huang
* @description 针对表【sys_file】的数据库操作Service实现
* @createDate 2025-12-03 15:11:02
*/
@Service
@Slf4j
public class SysFileServiceImpl extends ServiceImpl<SysFileMapper, SysFile> implements SysFileService{

    @Autowired
    private SysUserFileMapper sysUserFileMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private AuditLogProducer auditLogProducer;
    @Autowired
    private PulsarTemplate<String> pulsarTemplate;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Executor uploadExecutor;

    private static final RedisScript<Boolean> LOCK_SCRIPT;
    private static final RedisScript<Boolean> UNLOCK_SCRIPT;

    // 创建线程池，初始化LUA脚本
    static {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：根据CPU核数设置，IO密集型任务可以设大一点
        executor.setCorePoolSize(10);
        // 最大线程数
        executor.setMaxPoolSize(20);
        // 队列容量
        executor.setQueueCapacity(200);
        // 线程名前缀
        executor.setThreadNamePrefix("upload-worker-");
        // 拒绝策略：调用者运行（防止丢任务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        uploadExecutor = executor;

        LOCK_SCRIPT = RedisScript.of(new ClassPathResource("static/lockFile.lua"), Boolean.class);
        UNLOCK_SCRIPT = RedisScript.of(new ClassPathResource("static/unlockFile.lua"), Boolean.class);
    }

    // 内部类：用于封装单个文件的上传结果
    @Data
    private static class UploadResult {
        private String fileName;
        private long fileSize;
        private boolean success;
        private String errorMessage;

        public UploadResult(String fileName, long fileSize, boolean success, String errorMessage) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }

    @Override
    public UploadFilesResultDTO uploadFiles(List<MultipartFile> files, Long parentFileId, Long userId, String username) {
        // 1. 全局前置校验：用户空间配额预判
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        SysUser user = sysUserMapper.selectById(userId);
        if (user.getUsedSpace() + totalSize > user.getTotalSpace()) {
            throw new BusinessException("剩余空间不足，无法上传这批文件");
        }

        // 2. 并发处理每个文件
        List<CompletableFuture<UploadResult>> futures = files.stream()
                .map(file -> CompletableFuture.supplyAsync(
                        () -> handleSingleFileUpload(file, parentFileId, userId, username), uploadExecutor))
                .toList();

        // 3. 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 4. 汇总结果
        List<String> successFilesInfo = new ArrayList<>();
        List<String> failedFilesInfo = new ArrayList<>();
        for (CompletableFuture<UploadResult> future : futures) {
            try {
                UploadResult result = future.get();
                if (result.success) {
                    successFilesInfo.add("文件名:%s 大小:%.1fKB".formatted(result.fileName, result.fileSize / 1024.0));
                } else {
                    failedFilesInfo.add("文件名:%s 大小:%.1fKB. 失败原因:%s".formatted(result.fileName, result.fileSize / 1024.0, result.errorMessage));
                }
            } catch (Exception e) {
                log.error("获取并发结果异常", e);
            }
        }

        // 5. 构造dto，并返回
        return new UploadFilesResultDTO(
                files.size(),
                successFilesInfo.size(),
                successFilesInfo,
                failedFilesInfo
        );
    }

    @Override
    public List<UserFileVO> getFileList(Long userId, Long parentFileId) {
        log.info("Get file list: userId {}, parentFileId {}", userId, parentFileId);
        List<UserFileVO> userFileVOS = sysUserFileMapper.selectUserFileList(userId, parentFileId);
        if (userFileVOS == null){
            return Collections.emptyList();
        }
        // 按照是否为文件夹、修改时间倒序排序
        userFileVOS.sort((o1, o2) -> {
            if (o1.getIsFolder() == o2.getIsFolder()){
                // 同类型，按修改时间倒序
                return o2.getUpdatedAt().compareTo(o1.getUpdatedAt());
            }
            // 不同类型，文件夹优先
            return o1.getIsFolder() ? -1 : 1;
        });
        return userFileVOS;
    }

    @Override
    public List<UserFileVO> searchFile(Long userId, String keyword) {
        log.info("Searching file: {}", keyword);
        if (StrUtil.isBlank(keyword)){
            return Collections.emptyList();
        }
        List<UserFileVO> userFileVOS = sysUserFileMapper.searchByKeyword(userId, keyword);
        userFileVOS.sort((o1, o2) -> {
            if (o1.getIsFolder() == o2.getIsFolder()){
                // 同类型，按修改时间倒序
                return o2.getUpdatedAt().compareTo(o1.getUpdatedAt());
            }
            // 不同类型，文件夹优先
            return o1.getIsFolder() ? -1 : 1;
        });
        return userFileVOS;
    }

    @Override
    public void createFolder(String folderName, Long parentFileId, Long userId, String username) {
        log.info("Creating folder: {}", folderName);
        // 1. 校验同级目录下是否有重名文件（文件夹或文件）
        Long count = sysUserFileMapper.selectCount(new LambdaQueryWrapper<SysUserFile>()
                .eq(SysUserFile::getUserId, userId)
                .eq(SysUserFile::getParentFileId, parentFileId)
                .eq(SysUserFile::getFileName, folderName));

        if (count > 0) {
            throw new BusinessException("当前目录下已存在同名文件或文件夹");
        }
        // 2. 编程式事务：同时插入 sys_file 和 sys_user_file
        Long fileId = transactionTemplate.execute(status -> {
            try {
                // A. 插入 sys_file 虚记录 (占位符)
                SysFile virtualFile = new SysFile();
                virtualFile.setFileSize(0L);
                virtualFile.setRealPath(""); // 空字符串或特定标识
                virtualFile.setFileType(Constants.FOLDER);
                virtualFile.setCreatedAt(LocalDateTime.now());
                virtualFile.setUpdatedAt(LocalDateTime.now());
                baseMapper.insert(virtualFile);

                // B. 插入 sys_user_file 目录项
                SysUserFile sysUserFile = SysUserFile.builder()
                        .userId(userId)
                        .fileId(virtualFile.getFileId())
                        .role(UserRole.OWNER)
                        .fileName(folderName)
                        .parentFileId(parentFileId)
                        .isFolder(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                sysUserFileMapper.insert(sysUserFile);
                return virtualFile.getFileId();
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("创建文件夹失败", e);
                throw new RuntimeException("创建文件夹失败");
            }
        });
        // 3. 记录审计日志 (新建文件夹)
        auditLogProducer.recordLog(userId, username, fileId, OperationType.CREATE_FOLDER, "创建文件夹: " + folderName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserFile(Long userFileId, Long userId, String username) {
        log.info("Deleting user file: {}", userFileId);
        // 1. 查询目标记录 (用户视图)
        SysUserFile userFile = sysUserFileMapper.selectById(userFileId);
        if (userFile == null) {
            throw new BusinessException("文件不存在");
        }
        if (!userFile.getUserId().equals(userId)) {
            throw new BusinessException("无权删除");
        }
        // 2. 递归删除
        doDeleteRecursively(userFile, userId, username);
    }

    @Override
    @RequireFileRole(UserRole.VIEWER)
    public Pair<SysFile, String> getFileForDownload(Long userFileId, Long userId, String username, String action) {
        log.info("Getting file for download: {}", userFileId);
        // 1. AOP 已经校验了存在性和权限，直接查
        SysUserFile userFile = sysUserFileMapper.selectById(userFileId);
        // 2. 文件夹不能下载
        if (Boolean.TRUE.equals(userFile.getIsFolder())) {
            throw new BusinessException("不能下载文件夹");
        }
        // 3. 获取物理文件信息
        SysFile sysFile = baseMapper.selectById(userFile.getFileId());
        // 4. 记录日志 (下载操作关联 fileId)
        if (StrUtil.isBlank(action)){
            // 默认是下载
            action = "download";
        }
        action = action.toLowerCase();
        if ("preview".equals(action)){
            auditLogProducer.recordLog(userId, username, sysFile.getFileId(), OperationType.PREVIEW,
                    "预览文件:%s, 大小:%.1fKB".formatted(userFile.getFileName(), sysFile.getFileSize() / 1024.0));
        }else if ("download".equals(action)) {
            auditLogProducer.recordLog(userId, username, sysFile.getFileId(), OperationType.DOWNLOAD,
                    "下载文件: " + userFile.getFileName());
        }else {
            throw new BusinessException("action 参数错误");
        }
        // 5. 返回
        return Pair.of(sysFile, userFile.getFileName());
    }

    @Override
    @RequireFileRole(UserRole.EDITOR)
    @Transactional(rollbackFor = Exception.class)
    public void renameUserFile(Long userFileId, String newName, Long userId, String username) {
        log.info("Renaming user file: {}, new name: {}", userFileId, newName);
        // 1. 参数校验
        if (StrUtil.isBlank(newName)) {
            throw new BusinessException("文件名不能为空");
        }
        // 2. 查出当前对象 (AOP已保驾护航，肯定存在)
        SysUserFile userFile = sysUserFileMapper.selectById(userFileId);
        // 3. 校验同目录下重名 (除自己以外)
        Long count = sysUserFileMapper.selectCount(new LambdaQueryWrapper<SysUserFile>()
                .eq(SysUserFile::getUserId, userId)
                .eq(SysUserFile::getParentFileId, userFile.getParentFileId())
                .eq(SysUserFile::getFileName, newName)
                .ne(SysUserFile::getId, userFileId)); // 排除自己
        if (count > 0) {
            throw new BusinessException("该目录下已存在同名文件");
        }
        // 4. 修改名称
        String oldName = userFile.getFileName();
        userFile.setFileName(newName);
        userFile.setUpdatedAt(LocalDateTime.now());
        sysUserFileMapper.updateById(userFile);
        // 5. 记录日志
        auditLogProducer.recordLog(userId, username, userFile.getFileId(), OperationType.RENAME,
                "重命名: " + oldName + " -> " + newName);
    }

    @Override
    @RequireFileRole(UserRole.EDITOR)
    public void moveUserFile(Long userFileId, Long targetParentFileId, Long userId, String username) {
        log.info("Moving user file: {}, target parent file id: {}", userFileId, targetParentFileId);
        // 1. 获取源文件 (AOP 已校验存在性)
        SysUserFile sourceFile = sysUserFileMapper.selectById(userFileId);

        // 校验：不能移动到自己下面 (虽然后面有防环检测，这里先拦一道简单的)
        if (sourceFile.getFileId().equals(targetParentFileId)) {
            throw new BusinessException("不能移动到自身");
        }

        // 校验：目标目录不能是当前目录 (没移动)
        if (sourceFile.getParentFileId().equals(targetParentFileId)) {
            throw new BusinessException("文件已在目标目录下");
        }

        // 2. 校验目标文件夹合法性
        if (targetParentFileId != 0) {
            // 如果不是根目录，需要查目标文件夹是否存在，且必须是文件夹
            SysUserFile targetFolder = getByFileId(targetParentFileId, userId);
            if (targetFolder == null) {
                throw new BusinessException("目标文件夹不存在");
            }
            if (!Boolean.TRUE.equals(targetFolder.getIsFolder())) {
                throw new BusinessException("目标位置不是文件夹");
            }
        }

        // 3. 防环检测 (仅当源对象是文件夹时需要)
        if (Boolean.TRUE.equals(sourceFile.getIsFolder())) {
            checkMoveLoop(sourceFile.getFileId(), targetParentFileId, userId);
        }

        // 4. 重名校验 (目标目录下不能有同名文件)
        Long count = sysUserFileMapper.selectCount(new LambdaQueryWrapper<SysUserFile>()
                .eq(SysUserFile::getUserId, userId)
                .eq(SysUserFile::getParentFileId, targetParentFileId) // 目标目录
                .eq(SysUserFile::getFileName, sourceFile.getFileName()));

        if (count > 0) {
            throw new BusinessException("目标目录下已存在同名文件");
        }

        // 5. 执行移动
        Long originParentFileId = sourceFile.getParentFileId();
        sourceFile.setParentFileId(targetParentFileId);
        sourceFile.setUpdatedAt(LocalDateTime.now());
        sysUserFileMapper.updateById(sourceFile);

        // 6. 审计日志
        auditLogProducer.recordLog(userId, username, sourceFile.getFileId(), OperationType.MOVE,
                "移动文件: " + sourceFile.getFileName() + "。从" + originParentFileId + "目录移动到" + targetParentFileId + "目录");
    }

    @Override
    @RequireFileRole(UserRole.OWNER)
    @Transactional(rollbackFor = Exception.class)
    public void shareFile(Long userFileId, ShareRequestDto req, Long userId, String username) {
        log.info("Sharing file: {}", userFileId);
        // 1. 获取源文件 (AOP 已校验权限)
        SysUserFile sourceFile = sysUserFileMapper.selectById(userFileId);

        // 2. 校验：不能共享文件夹
        if (Boolean.TRUE.equals(sourceFile.getIsFolder())) {
            throw new BusinessException("暂时不支持共享文件夹");
        }

        // 3. 校验权限参数
        if (req.getTargetRole() == UserRole.OWNER) {
            throw new BusinessException("无法转让Owner权限");
        }

        // 4. 查找目标用户
        SysUser targetUser = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getTargetUsername()));
        if (targetUser == null) {
            throw new BusinessException("目标用户不存在");
        }
        if (targetUser.getUserId().equals(userId)) {
            throw new BusinessException("不能分享给自己");
        }

        // 5. 查重：目标用户是否已经是协作者？
        Long count = sysUserFileMapper.selectCount(new LambdaQueryWrapper<SysUserFile>()
                .eq(SysUserFile::getUserId, targetUser.getUserId())
                .eq(SysUserFile::getFileId, sourceFile.getFileId()));
        if (count > 0) {
            throw new BusinessException("该用户已是协作者，请勿重复添加");
        }

        // 6. 创建目标用户的视图
        SysUserFile targetUserFile = new SysUserFile();
        targetUserFile.setUserId(targetUser.getUserId());
        targetUserFile.setFileId(sourceFile.getFileId());
        targetUserFile.setParentFileId(0L); // 默认分享文件在用户的根目录
        targetUserFile.setFileName(sourceFile.getFileName());
        targetUserFile.setIsFolder(false);
        targetUserFile.setRole(req.getTargetRole());
        targetUserFile.setCreatedAt(LocalDateTime.now());
        targetUserFile.setUpdatedAt(LocalDateTime.now());
        sysUserFileMapper.insert(targetUserFile);

        // 7. 审计日志
        auditLogProducer.recordLog(userId, username, sourceFile.getFileId(), OperationType.SHARE,
                "分享文件给: " + targetUser.getUsername() + ", 权限: " + req.getTargetRole());
    }


    @Override
    @RequireFileRole(UserRole.OWNER)
    public void revokeShare(Long userFileId, Long targetUserId, String targetUsername, Long userId, String username) {
        log.info("Revoking share: {}, target user id: {}", userFileId, targetUserId);
        // 1. 获取源文件
        SysUserFile sourceFile = sysUserFileMapper.selectById(userFileId);

        // 2. 删除目标用户的记录
        if (targetUserId.equals(userId)) {
            throw new BusinessException("不能移除自己");
        }
        int rows = sysUserFileMapper.delete(new LambdaQueryWrapper<SysUserFile>()
                .eq(SysUserFile::getFileId, sourceFile.getFileId())
                .eq(SysUserFile::getUserId, targetUserId));
        if (rows == 0) {
            throw new BusinessException("目标用户并未持有该文件");
        }

        // 3. 审计日志
        auditLogProducer.recordLog(userId, username, sourceFile.getFileId(), OperationType.REVOKE_SHARE,
                "移除协作者: " + targetUsername + ", 协作者ID: " + targetUserId);
    }

    @Override
    @RequireFileRole(UserRole.OWNER)
    public void updateShareRole(Long userFileId, ShareRequestDto dto, Long userId, String username) {
        log.info("Updating share role: {}", userFileId);
        // 1. 获取源文件 (AOP 已校验权限)
        SysUserFile sourceFile = sysUserFileMapper.selectById(userFileId);

        // 2. 参数校验
        if (dto.getTargetRole() == null) {
            throw new BusinessException("目标权限不能为空");
        }
        if (dto.getTargetRole() == UserRole.OWNER) {
            throw new BusinessException("无法将协作者提升为OWNER");
        }
        if (dto.getTargetUserId().equals(userId)) {
            throw new BusinessException("不能调整自己的权限");
        }

        // 3. 查找目标协作者的记录
        // 条件：file_id 相同 AND user_id = targetUserId
        SysUserFile targetUserFile = sysUserFileMapper.selectOne(new LambdaQueryWrapper<SysUserFile>()
                .eq(SysUserFile::getFileId, sourceFile.getFileId())
                .eq(SysUserFile::getUserId, dto.getTargetUserId()));

        if (targetUserFile == null) {
            throw new BusinessException("该用户不是此文件的协作者");
        }

        // 4. 更新权限
        UserRole oldRole = targetUserFile.getRole();
        if (oldRole == dto.getTargetRole()) {
            return; // 权限没变，直接返回
        }

        targetUserFile.setRole(dto.getTargetRole());
        targetUserFile.setUpdatedAt(LocalDateTime.now());
        sysUserFileMapper.updateById(targetUserFile);

        // 5. 审计日志
        auditLogProducer.recordLog(userId, username, sourceFile.getFileId(), OperationType.UPDATE_PERMISSION,
                "调整用户[id:" + dto.getTargetUserId() + " " + dto.getTargetUsername() + "]权限: " + oldRole + " -> " + dto.getTargetRole());
    }

    @Override
    @RequireFileRole(UserRole.VIEWER)
    public List<ShareListVO> getShareList(Long userFileId, Long userId, String username) {
        log.info("Getting share list: {}", userFileId);
        // 逻辑保持不变，参数名已对齐
        SysUserFile sourceFile = sysUserFileMapper.selectById(userFileId);
        List<SysUserFile> shares = sysUserFileMapper.selectList(new LambdaQueryWrapper<SysUserFile>()
                .eq(SysUserFile::getFileId, sourceFile.getFileId()));
        // 按照权限LEVEL降序排序，OWNER最高，EDITOR次之，VIEWER最低
        shares.sort(((o1, o2) -> o2.getRole().getLevel() - o1.getRole().getLevel()));
        List<ShareListVO> result = new ArrayList<>();
        for (SysUserFile share : shares) {
            SysUser u = sysUserMapper.selectById(share.getUserId());
            if (u != null) {
                ShareListVO vo = new ShareListVO();
                vo.setUserId(u.getUserId());
                vo.setUsername(u.getUsername());
                vo.setRole(share.getRole());
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    @RequireFileRole(UserRole.VIEWER)
    public String getFileContent(Long userFileId, Long userId, String username) {
        log.info("Getting file content: {}", userFileId);
        // 1. 获取文件记录 (AOP已校验)
        SysUserFile userFile = sysUserFileMapper.selectById(userFileId);
        if (Boolean.TRUE.equals(userFile.getIsFolder())) {
            throw new BusinessException("文件夹不支持预览");
        }

        // 2. 尝试从 Redis 缓存读取
        String contentKey = Constants.FILE_CONTENT_KEY_PREFIX + userFile.getFileId();
        String cachedContent = redisTemplate.opsForValue().get(contentKey);
        if (cachedContent != null) {
            return cachedContent;
        }

        // 3. 读取磁盘
        SysFile sysFile = baseMapper.selectById(userFile.getFileId());
        String fullPath = STORAGE_ROOT + File.separator + sysFile.getRealPath();
        File file = new File(fullPath);
        if (!file.exists()) {
            throw new BusinessException("物理文件丢失");
        }
        if (!isTextFile(sysFile.getFileType())){
            throw new BusinessException("文件格式不支持在线预览，仅支持纯文本文件");
        }
        // 限制大小 < 1MB
        if (file.length() > 1024 * 1024) {
            throw new BusinessException("文件过大，不支持在线预览");
        }
        String content = FileUtil.readString(file, StandardCharsets.UTF_8);

        // 4. 写入缓存 (Read-Through)
        redisTemplate.opsForValue().set(contentKey, content, Constants.CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        // 5. 记录日志
        auditLogProducer.recordLog(userId, username, sysFile.getFileId(), OperationType.PREVIEW,
                "预览文件:%s, 大小:%.1fKB".formatted(userFile.getFileName(), file.length() / 1024.0));
        return content;
    }

    @Override
    @RequireFileRole(UserRole.EDITOR)
    public boolean lockFile(Long userFileId, Long userId) {
        log.info("Locking file: {}, userId {}", userFileId, userId);
        SysUserFile userFile = sysUserFileMapper.selectById(userFileId);
        String lockKey = Constants.FILE_LOCK_KEY_PREFIX + userFile.getFileId();
        // 执行 Lua 脚本
        Boolean success = redisTemplate.execute(
                LOCK_SCRIPT,
                Collections.singletonList(lockKey), // KEYS
                userId.toString(),                  // ARGV[1]
                String.valueOf(Constants.LOCK_TTL_SECONDS)    // ARGV[2]
        );
        return Boolean.TRUE.equals(success);
    }

    @Override
    @RequireFileRole(UserRole.EDITOR)
    public boolean unlockFile(Long userFileId, Long userId) {
        log.info("Unlocking file: {}, userId {}", userFileId, userId);
        SysUserFile userFile = sysUserFileMapper.selectById(userFileId);
        String lockKey = Constants.FILE_LOCK_KEY_PREFIX + userFile.getFileId();
        // 执行 Lua 脚本
        return redisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(lockKey), // KEYS
                userId.toString()                   // ARGV[1]
        );
    }

    @Override
    @RequireFileRole(UserRole.EDITOR)
    @Transactional(rollbackFor = Exception.class)
    public long saveFileContent(Long userFileId, String content, Long userId, String username) {
        log.info("Saving file content: {}, userId {}", userFileId, userId);
        // 1. 获取文件记录
        SysUserFile userFile = sysUserFileMapper.selectById(userFileId);
        String lockKey = Constants.FILE_LOCK_KEY_PREFIX + userFile.getFileId();
        String contentKey = Constants.FILE_CONTENT_KEY_PREFIX + userFile.getFileId();

        // 2. 严格校验锁：必须持有锁才能保存
        // 这里不需要原子删锁，所以普通 get 校验即可
        String lockOwner = redisTemplate.opsForValue().get(lockKey);
        if (lockOwner == null || !lockOwner.equals(userId.toString())) {
            throw new BusinessException("保存失败：编辑锁已失效或被他人抢占，请刷新页面");
        }

        // 3. 同步写磁盘
        SysFile sysFile = baseMapper.selectById(userFile.getFileId());
        String fullPath = STORAGE_ROOT + File.separator + sysFile.getRealPath();
        FileUtil.writeString(content, new File(fullPath), StandardCharsets.UTF_8);

        // 4. 更新元数据
        long newSize = new File(fullPath).length();
        Long oldSize = sysFile.getFileSize();
        long diff = newSize - oldSize;

        sysFile.setFileSize(newSize);
        sysFile.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateById(sysFile);

        userFile.setUpdatedAt(LocalDateTime.now());
        sysUserFileMapper.updateById(userFile);

        // 5. 更新配额 (找到 Owner)
        SysUserFile ownerUserFile = sysUserFileMapper.selectOne(new LambdaQueryWrapper<SysUserFile>()
                .eq(SysUserFile::getFileId, sysFile.getFileId())
                .eq(SysUserFile::getRole, UserRole.OWNER));
        if (ownerUserFile != null && diff != 0) {
            sysUserMapper.updateUsedSpace(ownerUserFile.getUserId(), diff);
        }

        // 6. 清理缓存 (Write-Invalidate)
        redisTemplate.delete(contentKey);

        // 7. 日志
        auditLogProducer.recordLog(userId, username, sysFile.getFileId(), OperationType.SAVE_CONTENT,
                "编辑保存：%s，大小: %.1fKB -> %.1fKB".formatted(userFile.getFileName(), oldSize / 1024.0, newSize / 1024.0));

        return newSize;
    }


    /**
     * 判断文件是否是文本文件
     * @param ext
     * @return
     */
    private boolean isTextFile(String ext) {
        if (ext == null) return false;
        ext = ext.toLowerCase();
        return Set.of("txt", "log", "rtf", "html", "htm", "css", "js", "svg",
                        "json", "xml", "csv", "yaml", "yml", "ini", "conf", "cfg",
                        "py", "java", "c", "cpp","cs","php","sql","go","rs",
                        "bat","sh","psl","md","tex")
                .contains(ext);
    }

    /**
     * 辅助方法：通过 fileId 和 userId 查找记录
     * @param fileId
     * @param userId
     * @return
     */
    private SysUserFile getByFileId(Long fileId, Long userId) {
        return sysUserFileMapper.selectOne(new LambdaQueryWrapper<SysUserFile>()
                .eq(SysUserFile::getUserId, userId)
                .eq(SysUserFile::getFileId, fileId)
                .last("LIMIT 1"));
    }

    /**
     * 辅助方法：检查移动时是否形成环
     * 逻辑：从 target 开始向上找父节点，如果路径中出现了 sourceFileId，说明 target 是 source 的子孙节点 -> 报错
     * @param sourceFileId
     * @param targetParentFileId
     * @param userId
     */
    private void checkMoveLoop(Long sourceFileId, Long targetParentFileId, Long userId) {
        Long currentCheckId = targetParentFileId;

        // 循环向上查找，直到根目录 (0)
        while (currentCheckId != 0) {
            if (currentCheckId.equals(sourceFileId)) {
                throw new BusinessException("无法移动：目标文件夹是源文件夹的子目录");
            }
            SysUserFile currentFolder = getByFileId(currentCheckId, userId);
            if (currentFolder == null) {
                // 路径断了，可能是数据异常，或者是根目录
                throw new BusinessException("目标文件夹路径异常。无法查找父目录信息");
            }
            currentCheckId = currentFolder.getParentFileId();
        }
    }

    /**
     * 递归删除
     * @param currentNode
     * @param userId
     * @param username
     */
    private void doDeleteRecursively(SysUserFile currentNode, Long userId, String username) {
        // A. 如果是文件夹，先递归删除子节点
        if (Boolean.TRUE.equals(currentNode.getIsFolder())) {
            // 找所有 parent_file_id = 当前节点的 file_id 的记录
            List<SysUserFile> children = sysUserFileMapper.selectList(new LambdaQueryWrapper<SysUserFile>()
                    .eq(SysUserFile::getUserId, userId)
                    .eq(SysUserFile::getParentFileId, currentNode.getFileId()));
            for (SysUserFile child : children) {
                doDeleteRecursively(child, userId, username);
            }
        }
        // B. 删除当前节点，OWNER会级联删除所有引用
        if (currentNode.getRole() == UserRole.OWNER) {
            handleOwnerDelete(currentNode, username);
        } else {
            handleCollaboratorDelete(currentNode, username);
        }
    }

    /**
     * OWNER删除逻辑
     * @param userFile
     * @param username
     */
    private void handleOwnerDelete(SysUserFile userFile, String username) {
        Long fileId = userFile.getFileId();

        // 1. 物理文件/配额处理 (先查出来)
        SysFile sysFile = baseMapper.selectById(fileId);

        // 2. 删除所有引用该 fileId 的视图 (级联删除协作者的记录)
        sysUserFileMapper.delete(new LambdaQueryWrapper<SysUserFile>().eq(SysUserFile::getFileId, fileId));

        // 3. 删除 sys_file (Inode)
        baseMapper.deleteById(fileId);

        // 4. 配额恢复与物理文件清理 (仅针对非文件夹)
        if (sysFile != null && !userFile.getIsFolder()) {
            sysUserMapper.updateUsedSpace(userFile.getUserId(), -sysFile.getFileSize());
            // 异步物理删除，sysFile.realPath中存的是相对路径
            String relativePath = sysFile.getRealPath();
            if (StrUtil.isBlank(relativePath)) return;
            String fullPath = STORAGE_ROOT + File.separator + relativePath;
            pulsarTemplate.sendAsync(Constants.FILE_DELETE_TOPIC, fullPath)
                    .whenComplete((msgId, ex) -> {
                        if (ex != null) {
                            // 仅打印日志，不回滚业务
                            log.error("删除文件MSG发送 Pulsar 失败: {}", fullPath, ex);
                        }else {
                            log.info("删除文件MSG发送 Pulsar 成功: msgId {}", msgId);
                        }
                    });
        }

        // 5. 审计日志 (注意参数顺序)
        String details = userFile.getIsFolder() ? "删除文件夹：" + userFile.getFileName() : "删除文件：" + userFile.getFileName();
        auditLogProducer.recordLog(userFile.getUserId(), username, fileId, OperationType.DELETE, details);
    }

    /**
     * 协作者删除逻辑
     * @param userFile
     * @param username
     */
    private void handleCollaboratorDelete(SysUserFile userFile, String username) {
        // 协作者只能删自己的视图
        sysUserFileMapper.deleteById(userFile.getId());
        auditLogProducer.recordLog(userFile.getUserId(), username, userFile.getFileId(), OperationType.DELETE,
                "主动移除共享: " + userFile.getFileName());
    }

    /**
     * 处理单个文件上传
     * @param file
     * @param parentFileId
     * @param userId
     * @param username
     * @return
     */
    private UploadResult handleSingleFileUpload(MultipartFile file, Long parentFileId, Long userId, String username) {
        String originalFilename = file.getOriginalFilename();
        long size = file.getSize();
        log.info("Uploading file: {}, size {}", originalFilename, size);

        // A. 校验重名
        Long exists = sysUserFileMapper.selectCount(new LambdaQueryWrapper<SysUserFile>()
                .eq(SysUserFile::getUserId, userId)
                .eq(SysUserFile::getParentFileId, parentFileId)
                .eq(SysUserFile::getFileName, originalFilename));

        if (exists > 0) {
            log.warn("文件重名：{}", originalFilename);
            return new UploadResult(originalFilename, size,false, "当前目录下已存在同名文件或文件夹");
        }

        // B. 物理落盘
        // 规划路径: /netdisk_storage/2023/12/03/uuid.ext
        String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileType = FileNameUtil.extName(originalFilename);
        String uuidName = IdUtil.simpleUUID() + "." + fileType;
        String relativePath = dateFolder + File.separator + uuidName;
        String fullPath = STORAGE_ROOT + File.separator + relativePath;

        // 创建目录并写入
        File destFile = new File(fullPath);
        // Hutool 自动创建父目录
        FileUtil.touch(destFile);
        try {
            file.transferTo(destFile);
        } catch (IOException e) {
            log.error("文件写入磁盘异常", e);
            return new UploadResult(originalFilename, size, false, "文件上传失败");
        }

        // 开启编程式事务：保证 数据库插入 和 配额更新 是原子的
        // 注意：物理文件落地通常不在数据库事务内，避免IO阻塞数据库连接
        Long fileId = transactionTemplate.execute(status -> {
            try {
                // C. 插入 sys_file (Inode)
                SysFile sysFile = new SysFile();
                // 存相对路径，方便迁移
                sysFile.setRealPath(relativePath);
                sysFile.setFileSize(size);
                sysFile.setFileType(fileType);
                sysFile.setCreatedAt(LocalDateTime.now());
                sysFile.setUpdatedAt(LocalDateTime.now());
                baseMapper.insert(sysFile);

                // D. 插入 sys_user_file (Dentry)
                SysUserFile sysUserFile = SysUserFile.builder()
                        .userId(userId)
                        .fileId(sysFile.getFileId())
                        .role(UserRole.OWNER)
                        .fileName(originalFilename)
                        .parentFileId(parentFileId)
                        .isFolder(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                sysUserFileMapper.insert(sysUserFile);

                // E. 更新用户配额
                sysUserMapper.updateUsedSpace(userId, size);

                log.info("上传文件且信息写入DB成功: {}, fileId {}", originalFilename, sysFile.getFileId());
                return sysFile.getFileId();
            } catch (Exception e) {
                // 回滚数据库
                status.setRollbackOnly();
                log.error("上传文件信息写入DB异常，回滚并删除文件", e);
                // 删除物理文件
                FileUtil.del(destFile);
                return -1L;
            }
        });

        // 记录审计日志 (上传文件)，并返回 UploadResult
        if (fileId == null || fileId <= 0){
            return new UploadResult(originalFilename, size, false, "上传文件失败");
        }else {
            String details = "文件名:%s 大小:%.1fKB".formatted(originalFilename, size / 1024.0);
            auditLogProducer.recordLog(userId, username, fileId, OperationType.UPLOAD, details);
            return new UploadResult(originalFilename, size, true, null);
        }
    }


}





package com.bread.networkfilesystem.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.bread.networkfilesystem.dto.*;
import com.bread.networkfilesystem.entity.SysFile;
import com.bread.networkfilesystem.exception.BusinessException;
import com.bread.networkfilesystem.service.SysFileService;
import com.bread.networkfilesystem.vo.UserFileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/file")
@Tag(name = "文件/文件夹管理")
public class SysFileController {

    @Autowired
    private SysFileService fileService;


    @PostMapping("/upload")
    @Operation(summary = "文件上传")
    public Result upload(@RequestParam List<MultipartFile> files,
                         @RequestParam(defaultValue = "0") Long parentFileId,
                         @RequestAttribute("userId") Long userId,
                         @RequestAttribute("username") String username) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        // 调用 Service，返回 DTO
        UploadFilesResultDTO resultDto = fileService.uploadFiles(files, parentFileId, userId, username);
        return Result.success(resultDto);
    }

    @PostMapping("/folder")
    @Operation(summary = "新建文件夹")
    public Result createFolder(@RequestBody FolderCreateDto dto,
                               @RequestAttribute("userId") Long userId,
                               @RequestAttribute("username") String username) {
        String folderName = dto.getFolderName();
        // 安全处理：parentId如果是null或空，默认为0
        Long temp = dto.getParentFileId();
        Long parentFileId = temp != null ? temp : 0L;
        if (folderName == null || StrUtil.isBlank(folderName)) {
            throw new BusinessException("文件夹名称不能为空");
        }
        fileService.createFolder(folderName, parentFileId, userId, username);
        return Result.successMsg("创建成功");
    }

    @GetMapping("/list")
    @Operation(summary = "获取文件列表")
    public Result list(@RequestParam(defaultValue = "0") Long parentFileId,
                       @RequestAttribute("userId") Long userId) {
        List<UserFileVO> list = fileService.getFileList(userId, parentFileId);
        return Result.success(list);
    }

    @GetMapping("/search")
    @Operation(summary = "文件搜索")
    public Result search(@RequestParam("keyword") String keyword,
                         @RequestAttribute("userId") Long userId) {
        List<UserFileVO> result = fileService.searchFile(userId, keyword);
        return Result.success(result);
    }

    @PostMapping("/rename")
    @Operation(summary = "重命名文件/文件夹")
    public Result rename(@RequestBody RenameDto dto,
                         @RequestAttribute("username") String username, // 审计用
                         @RequestAttribute("userId") Long userId) {
        Long userFileId = dto.getUserFileId();
        String newName = dto.getNewName();
        fileService.renameUserFile(userFileId, newName, userId, username);
        return Result.successMsg("重命名成功");
    }

    @PostMapping("/move")
    @Operation(summary = "文件移动")
    public Result move(@RequestBody FileMoveDto dto,
                       @RequestAttribute("username") String username,
                       @RequestAttribute("userId") Long userId) {
        // 解析参数
        Long userFileId = dto.getUserFileId();
        Long targetParentFileId = dto.getTargetParentFileId();
        fileService.moveUserFile(userFileId, targetParentFileId, userId, username);
        return Result.successMsg("移动成功");
    }

    @GetMapping("/download/{userFileId}")
    @Operation(summary = "文件下载")
    public void download(@PathVariable("userFileId") Long userFileId,
                         @RequestParam(value = "action", defaultValue = "download") String action,
                         @RequestAttribute("username") String username,
                         @RequestAttribute("userId") Long userId,
                         HttpServletResponse response) {
        try {
            // 1. 获取文件元数据 (Service 层已做鉴权)
            Pair<SysFile, String> fileForDownload = fileService.getFileForDownload(userFileId, userId, username, action);
            SysFile sysFile = fileForDownload.getFirst();
            String fileName = fileForDownload.getSecond();

            // 2. 物理文件读取
            String fullPath = SysFileService.STORAGE_ROOT + File.separator + sysFile.getRealPath();
            File file = new File(fullPath);
            if (!file.exists()) {
                throw new BusinessException("物理文件丢失");
            }

            // 3. 设置响应头
            response.reset();
            response.setContentType("application/octet-stream");
            response.setCharacterEncoding("utf-8");
            response.setContentLength((int) file.length());
            // 处理中文文件名乱码
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encodedFileName);

            // 4. 写流
            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[4096];
                int b;
                while ((b = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, b);
                }
            }

        } catch (Exception e) {
            // 下载接口报错比较麻烦，因为可能流已经写了一半
            // 如果还没写流，可以重置 Response 返回 JSON
            if (!response.isCommitted()) {
                response.reset();
                response.setContentType("application/json;charset=utf-8");
                try {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    Result result = Result.serverErr(e.getMessage());
                    response.getWriter().write(JSONUtil.toJsonStr(result));
                } catch (Exception ex) { /* ignore */ }
            }
        }
    }


    @DeleteMapping
    @Operation(summary = "删除用户文件")
    public Result delete(@RequestParam("userFileId") Long userFileId,
                         @RequestAttribute("username") String username,
                         @RequestAttribute("userId") Long userId) {
        // 调用 Service 执行递归删除逻辑
        fileService.deleteUserFile(userFileId, userId, username);
        return Result.successMsg("删除成功");
    }

    
    @GetMapping("/content")
    @Operation(summary = "预览文件内容", description = "预览文件内容，只支持大小<=1MB的纯文本文件")
    public Result getContent(@RequestParam("userFileId") Long userFileId,
                             @RequestAttribute("username") String username,
                             @RequestAttribute("userId") Long userId) {
        String content = fileService.getFileContent(userFileId, userId, username);
        return Result.success(content);
    }


    @PostMapping("/lock")
    @Operation(summary = "申请编辑文件，进入编辑模式", description = "尝试获取文件锁，获取成功才可编辑文件")
    public Result lock(@RequestBody FileLockDto dto,
                       @RequestAttribute("userId") Long userId) {
        Long userFileId = dto.getUserFileId();
        boolean success = fileService.lockFile(userFileId, userId);
        if (success) {
            return Result.successMsg("进入编辑模式");
        } else {
            // 返回特定的状态码或消息，前端据此提示“只读模式”
            return new Result(HttpServletResponse.SC_CONFLICT, "文件正在被他人编辑", null);
        }
    }

    @PostMapping("/unlock")
    @Operation(summary = "取消编辑文件，退出编辑模式", description = "释放文件锁")
    public Result unlock(@RequestBody FileLockDto dto,
                         @RequestAttribute("userId") Long userId) {
        Long userFileId = dto.getUserFileId();
        boolean result = fileService.unlockFile(userFileId, userId);
        return result ? Result.successMsg("退出编辑模式") : new Result(HttpServletResponse.SC_CONFLICT, "未持有锁", null);
    }


    @PostMapping("/save")
    @Operation(summary = "保存文件内容", description = "只有进入编辑模式才能保存文件内容")
    public Result saveContent(@RequestBody FileEditDto dto,
                              @RequestAttribute("username") String username,
                              @RequestAttribute("userId") Long userId) {
        Long userFileId = dto.getUserFileId();
        String content = dto.getContent();
        long newSize = fileService.saveFileContent(userFileId, content, userId, username);
        return Result.success(newSize);
    }



}

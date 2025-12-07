package com.bread.networkfilesystem.controller;

import com.bread.networkfilesystem.dto.Result;
import com.bread.networkfilesystem.dto.ShareRequestDto;
import com.bread.networkfilesystem.service.SysFileService;
import com.bread.networkfilesystem.vo.ShareListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/share")
@Tag(name = "文件分享")
public class ShareController {

    @Autowired
    private SysFileService fileService;

    @PostMapping("/add")
    @Operation(summary = "共享文件")
    public Result addShare(@RequestBody ShareRequestDto req,
                           @RequestAttribute("username") String username,
                           @RequestAttribute("userId") Long userId) {
        // 修改点：显式传递 userFileId 给 Service，以便 AOP 捕获
        fileService.shareFile(req.getUserFileId(), req, userId, username);
        return Result.successMsg("共享成功");
    }

    @PostMapping("/revoke")
    @Operation(summary = "撤销共享")
    public Result revokeShare(@RequestBody ShareRequestDto req,
                              @RequestAttribute("username") String username,
                              @RequestAttribute("userId") Long userId) {
        // 修改点：参数名 userId, username 已对齐
        fileService.revokeShare(req.getUserFileId(), req.getTargetUserId(), req.getTargetUsername(), userId, username);
        return Result.successMsg("已移除该协作者");
    }

    @PutMapping("/role")
    @Operation(summary = "更新共享权限")
    public Result updateRole(@RequestBody ShareRequestDto req,
                             @RequestAttribute("username") String username,
                             @RequestAttribute("userId") Long userId) {
        fileService.updateShareRole(req.getUserFileId(), req, userId, username);
        return Result.successMsg("权限调整成功");
    }

    @GetMapping("/list")
    @Operation(summary = "获取共享成员列表")
    public Result listShares(@RequestParam Long userFileId,
                             @RequestAttribute("username") String username,
                             @RequestAttribute("userId") Long userId) {
        // 修改点：传入 username
        List<ShareListVO> list = fileService.getShareList(userFileId, userId, username);
        return Result.success(list);
    }

}

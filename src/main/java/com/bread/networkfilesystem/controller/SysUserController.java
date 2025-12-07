package com.bread.networkfilesystem.controller;

import com.bread.networkfilesystem.dto.Result;
import com.bread.networkfilesystem.dto.UserDTO;
import com.bread.networkfilesystem.entity.SysUser;
import com.bread.networkfilesystem.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
@Tag(name = "用户管理")
public class SysUserController {

    @Autowired
    private SysUserService sysUserService;

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result register(@RequestBody UserDTO dto) {
        sysUserService.register(dto);
        return Result.successMsg("注册成功");
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result login(@RequestBody UserDTO dto) {
        String token = sysUserService.login(dto);
        // 封装返回数据
        Map<String, String> data = new HashMap<>();
        data.put("token", token);
        return Result.success(data);
    }


    @GetMapping("/info")
    @Operation(summary = "获取用户信息")
    public Result info(@RequestAttribute("userId") Long userId) {
        // userId 是通过拦截器从 Token 解析并放入 RequestAttribute 的
        SysUser userInfo = sysUserService.getUserInfo(userId);
        return Result.success(userInfo);
    }


}

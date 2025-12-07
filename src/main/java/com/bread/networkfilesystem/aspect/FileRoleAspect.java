package com.bread.networkfilesystem.aspect;

import com.bread.networkfilesystem.annotations.RequireFileRole;
import com.bread.networkfilesystem.entity.SysUserFile;
import com.bread.networkfilesystem.enums.UserRole;
import com.bread.networkfilesystem.exception.BusinessException;
import com.bread.networkfilesystem.service.SysUserFileService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;

@Aspect
@Component
@Slf4j
public class FileRoleAspect {

    @Autowired
    private SysUserFileService sysUserFileService;

    @Before("@annotation(com.bread.networkfilesystem.annotations.RequireFileRole)")
    public void checkPermission(JoinPoint joinPoint) {
        // 1. 获取注解上的要求权限
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireFileRole annotation = method.getAnnotation(RequireFileRole.class);
        UserRole requiredRole = annotation.value();

        // 2. 解析方法参数，获取 userFileId 和 userId
        Object[] args = joinPoint.getArgs();
        String[] paramNames = signature.getParameterNames();

        Long userFileId = null;
        Long userId = null;

        for (int i = 0; i < args.length; i++) {
            if ("userFileId".equals(paramNames[i]) && args[i] instanceof Long) {
                userFileId = (Long) args[i];
            } else if ("userId".equals(paramNames[i]) && args[i] instanceof Long) {
                userId = (Long) args[i];
            }
        }

        if (userFileId == null || userId == null) {
            throw new BusinessException("系统错误：权限校验失败，方法参数缺失 userFileId 或 userId");
        }

        // 3. 查询用户文件记录
        SysUserFile userFile = sysUserFileService.getById(userFileId);
        if (userFile == null) {
            throw new BusinessException("文件不存在或已被删除");
        }

        // 4. 校验归属权
        if (!Objects.equals(userFile.getUserId(), userId)) {
            throw new BusinessException("无权访问该文件");
        }

        // 5. 校验权限等级 (当前角色等级 >= 需要等级)
        if (!userFile.getRole().hasPermission(requiredRole)) {
            throw new BusinessException("权限不足，需要 " + requiredRole.name() + " 权限");
        }
    }


}

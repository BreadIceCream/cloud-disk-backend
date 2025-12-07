package com.bread.networkfilesystem.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bread.networkfilesystem.constant.Constants;
import com.bread.networkfilesystem.dto.UserDTO;
import com.bread.networkfilesystem.entity.SysUser;
import com.bread.networkfilesystem.exception.BusinessException;
import com.bread.networkfilesystem.service.SysUserService;
import com.bread.networkfilesystem.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
* @author huang
* @description 针对表【sys_user】的数据库操作Service实现
* @createDate 2025-12-03 15:11:02
*/
@Service
@Slf4j
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService{

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${jwt.secret-key}")
    private String secretKey;

    @Override
    public void register(UserDTO dto) {
        log.info("User register: {}", dto);
        Assert.notNull(dto, "用户信息不能为空");
        Assert.notBlank(dto.getUsername(), "用户名不能为空");
        Assert.notBlank(dto.getPassword(), "密码不能为空");
        // 1. 校验用户名唯一性
        Long count = lambdaQuery().eq(SysUser::getUsername, dto.getUsername()).count();
        if (count > 0) {
            // 直接抛出业务异常，ControllerAdvice 会处理
            throw new BusinessException("用户名 " + dto.getUsername() + " 已存在");
        }
        // 2. 密码加密 (使用 Hutool BCrypt)
        String passwordHash = BCrypt.hashpw(dto.getPassword());
        // 3. 构建用户实体
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordHash);
        // 默认 1GB (1024*1024*1024)
        user.setTotalSpace(1073741824L);
        user.setUsedSpace(0L);
        user.setCreatedAt(LocalDateTime.now());

        // 4. 保存到数据库
        if (save(user)) {
            log.info("用户注册成功: {}", user);
        }else {
            throw new BusinessException("注册失败");
        }
    }

    @Override
    public String login(UserDTO dto) {
        log.info("User login: {}", dto);
        Assert.notNull(dto, "用户信息不能为空");
        Assert.notBlank(dto.getUsername(), "用户名不能为空");
        Assert.notBlank(dto.getPassword(), "密码不能为空");
        // 1. 查询用户
        SysUser user = lambdaQuery().eq(SysUser::getUsername, dto.getUsername()).one();
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 2. 校验密码
        if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 3. 生成 JWT Token
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getUserId());
        payload.put("username", user.getUsername());
        // Hutool 会自动添加 exp (过期时间) 等标准 Claims，这里设置 Key
        String token = JWTUtil.createToken(payload, secretKey.getBytes());

        // 4. 将 Token 存入 Redis (实现服务端管理，比如强制下线)
        // Key 格式: login:token:{userId}
        String redisKey = Constants.USER_LOGIN_REDIS_KEY + user.getUserId();
        redisTemplate.opsForValue().set(redisKey, token, 24, TimeUnit.HOURS);

        return token;
    }

    @Override
    public SysUser getUserInfo(Long userId) {
        log.info("Get user info: {}", userId);
        Assert.notNull(userId, "用户 ID 不能为空");
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 脱敏：不返回密码
        user.setPassword(null);
        return user;
    }
}





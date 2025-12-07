package com.bread.networkfilesystem.mq;

import com.bread.networkfilesystem.constant.Constants;
import com.bread.networkfilesystem.entity.SysLog;
import com.bread.networkfilesystem.enums.OperationType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Component
@Slf4j
public class AuditLogProducer {

    @Autowired
    private PulsarTemplate<SysLog> pulsarTemplate;

    /**
     * 发送审计日志到 Pulsar
     *
     * @param userId
     * @param username
     * @param fileId
     * @param op
     * @param details
     */
    public void recordLog(Long userId, String username, Long fileId, OperationType op, String details) {
        try {
            // 1. 自动获取 IP (Spring Web 环境下有效)
            String ip = "127.0.0.1";
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                ip = getIpAddress(request);
            }

            // 2. 构建实体
            SysLog sysLog = SysLog.builder()
                    .userId(userId)
                    .username(username)
                    .fileId(fileId)
                    .operation(op == null ? OperationType.OTHER : op)
                    .details(details)
                    .clientIp(ip)
                    .createdAt(LocalDateTime.now())
                    .build();

            // 3. 异步发送 (Fire and Forget)
            pulsarTemplate.sendAsync(Constants.AUDIT_LOG_TOPIC, sysLog)
                    .whenComplete((msgId, ex) -> {
                        if (ex != null) {
                            // 仅打印日志，不回滚业务
                            log.error("审计日志MSG发送 Pulsar 失败: {}", sysLog, ex);
                        }else {
                            log.info("审计日志MSG发送 Pulsar 成功: {}", msgId);
                        }
                    });
        } catch (Exception e) {
            // 兜底捕获，防止获取IP或构建对象时报错影响主业务
            log.error("构建审计日志异常", e);
        }
    }

    // 简易的 IP 获取工具方法
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

}

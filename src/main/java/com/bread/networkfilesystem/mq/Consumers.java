package com.bread.networkfilesystem.mq;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.bread.networkfilesystem.constant.Constants;
import com.bread.networkfilesystem.entity.SysLog;
import com.bread.networkfilesystem.service.SysLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.schema.SchemaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Slf4j
public class Consumers {

    @Autowired
    private SysLogService sysLogService;

    private static final String AUDIT_LOG_SUBSCRIPTION_NAME = "sub-audit-log-db-writer";
    private static final String FILE_DELETE_SUBSCRIPTION_NAME = "sub-file-delete-worker";

    /**
     * 处理审计日志
     * @param sysLogMsg
     */
    @PulsarListener(
            topics = Constants.AUDIT_LOG_TOPIC,
            subscriptionName = AUDIT_LOG_SUBSCRIPTION_NAME,
            subscriptionType = SubscriptionType.Shared,
            concurrency = "2"
    )
    public void handleAuditLog(Message<SysLog> sysLogMsg) {
        try {
            if (sysLogMsg == null) return;
            SysLog sysLog = sysLogMsg.getValue();
            if (sysLog == null) return;
            // 写入数据库
            if (sysLogService.save(sysLog)) {
                log.info("审计日志入库成功: msgId {}", sysLogMsg.getMessageId());
            }
        } catch (Exception e) {
            log.error("审计日志入库失败: msgId {}", sysLogMsg.getMessageId(), e);
            // 抛出异常会触发 Pulsar 的重试机制 (Nack)
            throw e;
        }
    }


    /**
     * 处理文件删除
     * @param realPathMsg
     */
    @PulsarListener(
            topics = Constants.FILE_DELETE_TOPIC,
            subscriptionName = FILE_DELETE_SUBSCRIPTION_NAME,
            subscriptionType = SubscriptionType.Shared,
            concurrency = "2"
    )
    public void handleFileDelete(Message<String> realPathMsg) {
        if (realPathMsg == null) return;
        String realPath = realPathMsg.getValue();
        if (realPath == null || StrUtil.isBlank(realPath)) return;
        // 注意：生产者发过来的是绝对路径
        try {
            File file = new File(realPath);
            if (file.exists()) {
                boolean deleted = FileUtil.del(file);
                log.info("物理文件删除{}: {}", deleted ? "成功" : "失败", realPath);
            } else {
                log.warn("物理文件不存在，无需删除: {}", realPath);
            }
        } catch (Exception e) {
            log.error("物理文件删除异常: {}", realPath, e);
            // 这里通常不需要重试太多次，删不掉可能是被占用，记录日志人工处理即可
        }
    }


}

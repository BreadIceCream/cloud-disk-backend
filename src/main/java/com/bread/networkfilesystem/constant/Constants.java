package com.bread.networkfilesystem.constant;

public class Constants {

    public static final String USER_LOGIN_REDIS_KEY = "login:token:";
    public static final String FOLDER = "FOLDER";
    public static final String AUDIT_LOG_TOPIC = "audit-log";
    public static final String FILE_DELETE_TOPIC = "file-delete";
    public static final String FILE_LOCK_KEY_PREFIX = "file:lock:";
    public static final String FILE_CONTENT_KEY_PREFIX = "file:content:";
    public static final long LOCK_TTL_SECONDS = 300; // 锁超时 5分钟
    public static final long CACHE_TTL_MINUTES = 30; // 内容缓存 30分钟
}

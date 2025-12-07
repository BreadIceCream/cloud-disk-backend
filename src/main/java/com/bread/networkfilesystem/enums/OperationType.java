package com.bread.networkfilesystem.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum OperationType {

    // === 文件核心操作 ===
    UPLOAD("UPLOAD", "文件上传"),
    DOWNLOAD("DOWNLOAD", "文件下载"),
    PREVIEW("PREVIEW", "文件预览"),
    DELETE("DELETE", "文件/文件夹删除"),
    SAVE_CONTENT("SAVE_CONTENT", "保存文件内容"),

    // === 目录结构操作 ===
    CREATE_FOLDER("CREATE_FOLDER", "新建文件夹"),
    RENAME("RENAME", "重命名"),
    MOVE("MOVE", "移动文件"),

    // === 协作分享操作 ===
    SHARE("SHARE", "发起共享"),
    REVOKE_SHARE("REVOKE_SHARE", "取消共享"), // 或移除协作者
    UPDATE_PERMISSION("UPDATE_PERMISSION", "更新协作权限"), // 如从 Viewer 改为 Editor

    // === 其他操作 ===
    OTHER("OTHER", "其他操作");

    @EnumValue
    private final String value;
    private final String desc;

    OperationType(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
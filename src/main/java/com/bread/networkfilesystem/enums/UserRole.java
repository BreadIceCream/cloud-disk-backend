package com.bread.networkfilesystem.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum UserRole {

    VIEWER("VIEWER", 1),
    EDITOR("EDITOR", 2),
    OWNER("OWNER", 3);

    @EnumValue
    private final String value;
    private final int level;

    UserRole(String value, int level) {
        this.value = value;
        this.level = level;
    }

    /**
     * 判断当前角色是否有足够的权限
     * @param requiredRole 需要的最低权限
     * @return true if 当前等级 >= 需要等级
     */
    public boolean hasPermission(UserRole requiredRole) {
        return this.level >= requiredRole.level;
    }

}

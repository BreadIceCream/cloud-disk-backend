package com.bread.networkfilesystem.annotations;

import com.bread.networkfilesystem.enums.UserRole;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireFileRole {

    // 执行该方法所需的最低权限角色
    UserRole value();

}

package com.bread.networkfilesystem.vo;

import com.bread.networkfilesystem.enums.UserRole;
import lombok.Data;

@Data
public class ShareListVO {
    private Long userId;
    private String username;
    private UserRole role;
}
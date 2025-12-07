package com.bread.networkfilesystem;

import com.bread.networkfilesystem.dto.UserDTO;
import com.bread.networkfilesystem.entity.SysUserFile;
import com.bread.networkfilesystem.service.SysUserFileService;
import com.bread.networkfilesystem.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NetworkFileSystemApplicationTests {

    @Autowired
    private SysUserFileService sysUserFileService;

    @Autowired
    private SysUserService sysUserService;

    @Test
    void contextLoads() {
    }

    @Test
    void insertUsers(){
        sysUserService.register(new UserDTO("admin", "admin"));
        sysUserService.register(new UserDTO("zhangsan", "zhangsan"));
        sysUserService.register(new UserDTO("lisi", "lisi"));
        sysUserService.register(new UserDTO("wangwu", "wangwu"));
        sysUserService.register(new UserDTO("zhaoliu", "zhaoliu"));
        sysUserService.register(new UserDTO("tianqi", "tianqi"));
        sysUserService.register(new UserDTO("sunba", "sunba"));
    }


}

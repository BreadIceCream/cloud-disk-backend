package com.bread.networkfilesystem;

import java.time.LocalDate;

public class Test {

    public static void main(String[] args) {
        String msg = "%s. 大小:%.2fMB. 失败原因:%s".formatted("picture01.png", 1024 / 1024.0, "文件已存在");
        System.out.println(msg);
        LocalDate.now();
    }

}

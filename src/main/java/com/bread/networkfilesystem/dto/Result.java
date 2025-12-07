package com.bread.networkfilesystem.dto;

import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;

@Data
public class Result {

    private int code;
    private String message;
    private Object data;

    public Result(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static Result success(Object data) {
        return new Result(HttpServletResponse.SC_OK, "success", data);
    }

    public static Result successMsg(String message){
        return new Result(HttpServletResponse.SC_OK, message, null);
    }

    public static Result serverErr(String message){
        return new Result(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, null);
    }

}

package com.bread.networkfilesystem.config;

import com.bread.networkfilesystem.dto.Result;
import com.bread.networkfilesystem.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.bread.networkfilesystem.controller")
@Slf4j
public class ControllerAdvice {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(code = HttpStatus.BAD_REQUEST)
    public Result handleBusinessException(BusinessException e) {
        log.warn("业务异常: ", e);
        return new Result(HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(code = HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleException(Exception e) {
        log.error("系统异常: ", e);
        return new Result(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "系统异常", null);
    }

}

package com.example.leaseplatform.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常：携带业务码 + 对外消息 + HTTP 状态，由 GlobalExceptionHandler 统一兜底。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final HttpStatus status;

    public BizException(int code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.INVALID_PARAMS, message, HttpStatus.BAD_REQUEST);
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    /** 未登录 / 令牌无效或过期（HTTP 401） */
    public static BizException unauthorized(String message) {
        return new BizException(ErrorCode.UNAUTHORIZED, message, HttpStatus.UNAUTHORIZED);
    }

    /** 已登录但无权限（HTTP 403） */
    public static BizException forbidden(String message) {
        return new BizException(ErrorCode.FORBIDDEN, message, HttpStatus.FORBIDDEN);
    }

    public static BizException conflict(String message) {
        return new BizException(ErrorCode.CONFLICT, message, HttpStatus.CONFLICT);
    }
}

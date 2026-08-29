package com.example.leaseplatform.common;

/**
 * 业务错误码。
 * HTTP 状态码与业务码解耦：同一语义错误（如参数错误）用固定业务码，
 * HTTP 状态由 GlobalExceptionHandler 按异常类型映射。
 */
public final class ErrorCode {

    /** 成功 */
    public static final int OK = 0;

    /** 参数错误（含校验失败，HTTP 422） */
    public static final int INVALID_PARAMS = 40000;

    /** 未登录 / 令牌无效或过期（HTTP 401） */
    public static final int UNAUTHORIZED = 40100;

    /** 资源不存在（HTTP 404） */
    public static final int NOT_FOUND = 40400;

    /** 状态冲突 / 存在依赖不允许操作（HTTP 409） */
    public static final int CONFLICT = 40900;

    /** 服务器内部错误（HTTP 500） */
    public static final int INTERNAL_ERROR = 50000;

    private ErrorCode() {
    }
}

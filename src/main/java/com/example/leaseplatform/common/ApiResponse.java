package com.example.leaseplatform.common;

import lombok.Data;

/**
 * 统一 API 响应格式：{code, message, data}。
 * code=0 表示成功；非 0 见 {@link ErrorCode}。参照 campus_express 的 response 包。
 */
@Data
public class ApiResponse<T> {

    /** 业务码：0 成功 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 数据（成功时携带；全局 jackson 配置 non_null，空值自动省略） */
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = ErrorCode.OK;
        r.message = "成功";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        return r;
    }
}

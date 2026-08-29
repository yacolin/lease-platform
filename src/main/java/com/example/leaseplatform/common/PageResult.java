package com.example.leaseplatform.common;

import java.util.List;

/**
 * 分页结果：{total, list}。
 */
public record PageResult<T>(long total, List<T> list) {

    public static <T> PageResult<T> of(long total, List<T> list) {
        return new PageResult<>(total, list);
    }
}

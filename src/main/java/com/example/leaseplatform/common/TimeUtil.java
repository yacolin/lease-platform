package com.example.leaseplatform.common;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 时间工具：数据库 DATETIME（Asia/Shanghai，与 JDBC serverTimezone 一致）
 * 统一转为 epoch 毫秒时间戳返回，避免各端解析字符串时间。
 */
public final class TimeUtil {

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    private TimeUtil() {
    }

    /** LocalDateTime -> epoch 毫秒（null 保持 null） */
    public static Long toEpochMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(DB_ZONE).toInstant().toEpochMilli();
    }
}

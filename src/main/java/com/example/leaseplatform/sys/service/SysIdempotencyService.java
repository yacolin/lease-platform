package com.example.leaseplatform.sys.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.leaseplatform.sys.entity.SysIdempotency;
import com.example.leaseplatform.sys.mapper.SysIdempotencyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 幂等服务（sys_idempotency，1.2）：外部重复回调/请求去重（roadmap 1.2.4 / 4.5）。
 * <p>
 * 用法：{@link #acquire} 成功（插入处理中记录）→ 执行业务（同事务）→ {@link #complete}；
 * 业务抛异常时事务回滚，记录自动消失，下次重试可重新 acquire。
 * 已存在记录（处理中或成功）时 acquire 返回 false，调用方应直接幂等返回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysIdempotencyService {

    public static final int STATUS_PROCESSING = 0;
    public static final int STATUS_SUCCESS = 1;

    private final SysIdempotencyMapper mapper;

    /**
     * 尝试获取幂等权：key 不存在 → 插入处理中记录并返回 true；
     * key 已存在（并发或重复请求）→ 返回 false。
     */
    @Transactional
    public boolean acquire(String key, String bizType, Long bizId, String requestHash, LocalDateTime expiredAt) {
        SysIdempotency existing = mapper.selectOne(new LambdaQueryWrapper<SysIdempotency>()
                .eq(SysIdempotency::getIdempotencyKey, key));
        if (existing != null) {
            return false;
        }
        SysIdempotency record = new SysIdempotency();
        record.setIdempotencyKey(key);
        record.setBizType(bizType);
        record.setBizId(bizId);
        record.setRequestHash(requestHash);
        record.setStatus(STATUS_PROCESSING);
        record.setExpiredAt(expiredAt);
        try {
            mapper.insert(record);
            return true;
        } catch (DuplicateKeyException e) {
            // 并发下唯一键兜底：视为重复请求
            return false;
        }
    }

    /** 标记处理成功（记录结果 JSON） */
    @Transactional
    public void complete(String key, String response) {
        mapper.update(null, new LambdaUpdateWrapper<SysIdempotency>()
                .eq(SysIdempotency::getIdempotencyKey, key)
                .set(SysIdempotency::getStatus, STATUS_SUCCESS)
                .set(SysIdempotency::getResponse, response));
    }

    /** 请求内容 SHA-256（防重放：同 key 不同内容视为异常重放） */
    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}

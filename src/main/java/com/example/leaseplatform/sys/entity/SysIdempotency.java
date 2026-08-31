package com.example.leaseplatform.sys.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等记录（sys_idempotency，1.2 交易可靠性）：外部重复回调/请求去重
 * （微信回调、充值回调、退款回调等），配合业务表状态乐观更新保证
 * "重复请求不产生重复资金变动"（roadmap 1.2.4 / 4.5）。
 * idempotency_key 唯一；request_hash 为请求内容 SHA-256（防重放）。
 */
@Data
@TableName("sys_idempotency")
public class SysIdempotency {

    /** 雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 幂等键（唯一，如 WX_NOTIFY:{out_trade_no}） */
    private String idempotencyKey;

    /** 业务类型（如 RECHARGE_NOTIFY / REFUND） */
    private String bizType;

    /** 业务单 ID */
    private Long bizId;

    /** 请求内容哈希（SHA-256，防重放） */
    private String requestHash;

    /** 状态：0-处理中, 1-成功 */
    private Integer status;

    /** 处理结果（JSON） */
    private String response;

    /** 过期时间 */
    private LocalDateTime expiredAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

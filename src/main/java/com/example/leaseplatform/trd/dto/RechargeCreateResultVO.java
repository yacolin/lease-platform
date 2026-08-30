package com.example.leaseplatform.trd.dto;

import lombok.Data;

/**
 * 充值下单结果：记录 + 微信 JSAPI 调起支付参数（微信支付未配置时为 null，走 mock-pay）。
 */
@Data
public class RechargeCreateResultVO {

    /** 充值记录 */
    private RechargeRecordVO record;

    /** 微信支付未配置 / 未返回 prepay 时为空，前端走 mock-pay */
    private PayParamsVO prepayParams;

    @Data
    public static class PayParamsVO {
        /** 时间戳（秒） */
        private String timeStamp;
        /** 随机串 */
        private String nonceStr;
        /** 预支付会话标识 */
        private String packageValue;
        /** 签名类型（RSA） */
        private String signType;
        /** 签名 */
        private String paySign;
    }
}

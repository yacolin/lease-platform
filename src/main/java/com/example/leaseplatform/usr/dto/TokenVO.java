package com.example.leaseplatform.usr.dto;

import lombok.Data;

/**
 * 登录 / 刷新成功返回的令牌对。
 */
@Data
public class TokenVO {

    /** access token（请求头 Authorization: Bearer <accessToken>） */
    private String accessToken;

    /** refresh token（用于刷新 / 登出，服务端存 Redis） */
    private String refreshToken;

    /** token 类型，固定 Bearer */
    private String tokenType;

    /** access token 有效秒数 */
    private long expiresIn;

    public static TokenVO of(String accessToken, String refreshToken, long expiresIn) {
        TokenVO vo = new TokenVO();
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setTokenType("Bearer");
        vo.setExpiresIn(expiresIn);
        return vo;
    }
}

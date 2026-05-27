package com.sanshuiyuan.user.infra.wx;

/** 微信登录失败（jscode2session 返回错误码 / 响应异常）。带业务错误码供前端区分。 */
public class WxAuthException extends RuntimeException {
    private final String code;

    public WxAuthException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

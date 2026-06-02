package com.sanshuiyuan.cend.referral;

/**
 * ref_id 解码失败：签名不符、格式非法或不可解析。
 *
 * <p>调用方据此降级为「自然流量」处理（绑定降级逻辑见 008b），不得据此暴露其它用户信息，
 * 也不得阻断注册主流程。
 */
public class InvalidRefIdException extends RuntimeException {

    public InvalidRefIdException(String message) {
        super(message);
    }

    public InvalidRefIdException(String message, Throwable cause) {
        super(message, cause);
    }
}

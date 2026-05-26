package com.sanshuiyuan.h5.referral;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 推广 ref_id 加解密：以 HMAC-SHA256 签名 + Base64URL 编码，使 ref_id 防篡改、不可猜测其它用户 id。
 *
 * <p>token 形如 {@code <payload>.<sig>}：
 * <ul>
 *   <li>{@code payload} = Base64URL(userId 的十进制字符串)；</li>
 *   <li>{@code sig}     = Base64URL(HMAC-SHA256(payloadBytes, secret))。</li>
 * </ul>
 * 服务端用同一密钥重算签名并以恒定时间比较校验；任何不符/格式非法即抛 {@link InvalidRefIdException}。
 *
 * <p>密钥经构造器注入（见 {@code ReferralConfig}），<b>不在本类硬编码</b>。
 * 本类无状态、线程安全（每次调用新建 {@link Mac} 实例）。
 */
public class RefIdCodec {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();

    private final byte[] secret;

    public RefIdCodec(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("ref_id 签名密钥不可为空");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 将推广者 userId 编码为防篡改 ref_id。 */
    public String encode(long userId) {
        byte[] payload = Long.toString(userId).getBytes(StandardCharsets.UTF_8);
        return B64URL.encodeToString(payload) + "." + B64URL.encodeToString(sign(payload));
    }

    /**
     * 校验签名并还原推广者 userId。
     *
     * @throws InvalidRefIdException token 为空、格式非法、签名不符或 payload 不可解析为 userId。
     */
    public long decode(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidRefIdException("ref_id 为空");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) {
            throw new InvalidRefIdException("ref_id 格式非法");
        }
        byte[] payload;
        byte[] presentedSig;
        try {
            payload = B64URL_DEC.decode(parts[0]);
            presentedSig = B64URL_DEC.decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new InvalidRefIdException("ref_id Base64URL 解码失败", e);
        }
        byte[] expectedSig = sign(payload);
        // 恒定时间比较，避免计时侧信道。
        if (!MessageDigest.isEqual(expectedSig, presentedSig)) {
            throw new InvalidRefIdException("ref_id 签名校验失败");
        }
        try {
            return Long.parseLong(new String(payload, StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            throw new InvalidRefIdException("ref_id payload 不是合法 userId", e);
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            return mac.doFinal(payload);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }
}

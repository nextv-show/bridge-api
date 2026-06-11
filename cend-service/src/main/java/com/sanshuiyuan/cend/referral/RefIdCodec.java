package com.sanshuiyuan.cend.referral;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 推广 ref_id 加解密：以 HMAC-SHA256 签名 + Base64URL 编码，使 ref_id 防篡改、不可猜测其它用户 id。
 *
 * <p>支持两种 token 形态（均 {@code <payload>.<sig>}，{@link #decode} 自动识别）：
 * <ul>
 *   <li><b>标准</b>（H5 分享链接 ?ref_id=）：{@code payload}=Base64URL(userId 十进制字符串)、
 *       {@code sig}=Base64URL(HMAC-SHA256 全 32 字节)。长度约 48 字符。</li>
 *   <li><b>紧凑</b>（{@link #encodeScene} 用于微信小程序码 scene）：{@code payload}=Base64URL(userId 大端最小字节)、
 *       {@code sig}=Base64URL(HMAC-SHA256 截断前 {@value #SCENE_SIG_BYTES} 字节)。
 *       总长 ≤32，满足微信 wxacode.getUnlimited 对 scene「最长 32 字符」的硬限制（否则报 40169）。</li>
 * </ul>
 * 服务端用同一密钥重算签名并以恒定时间比较校验；任何不符/格式非法即抛 {@link InvalidRefIdException}。
 * 两形态以「sig 字节长度」区分（标准 32 / 紧凑 {@value #SCENE_SIG_BYTES}），无歧义。
 *
 * <p>密钥经构造器注入（见 {@code ReferralConfig}），<b>不在本类硬编码</b>。
 * 本类无状态、线程安全（每次调用新建 {@link Mac} 实例）。
 */
public class RefIdCodec {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();

    /** 紧凑 scene 形态的截断 HMAC 字节数（80 位，足够防伪造且无验证预言机）。 */
    private static final int SCENE_SIG_BYTES = 10;

    private final byte[] secret;

    public RefIdCodec(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("ref_id 签名密钥不可为空");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 将推广者 userId 编码为防篡改 ref_id（标准形态，用于 H5 分享链接）。 */
    public String encode(long userId) {
        byte[] payload = Long.toString(userId).getBytes(StandardCharsets.UTF_8);
        return B64URL.encodeToString(payload) + "." + B64URL.encodeToString(sign(payload));
    }

    /**
     * 将推广者 userId 编码为紧凑防篡改 ref_id（≤32 字符），专用于微信小程序码 scene。
     *
     * <p>微信 wxacode.getUnlimited 的 scene 最长 32 字符（超出报 errcode 40169），标准 {@link #encode}
     * 约 48 字符会被拒。此处用「userId 大端最小字节 + 截断 {@value #SCENE_SIG_BYTES} 字节 HMAC」压缩，
     * 任意 long 都 ≤32（payload≤11 + 1 + sig 14 字符）。Base64URL 字符集（{@code A-Za-z0-9-_} 与 {@code .}）
     * 均在微信 scene 允许字符内。{@link #decode} 可还原。
     */
    public String encodeScene(long userId) {
        byte[] payload = minimalBigEndian(userId);
        byte[] sig = java.util.Arrays.copyOf(sign(payload), SCENE_SIG_BYTES);
        return B64URL.encodeToString(payload) + "." + B64URL.encodeToString(sig);
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
        byte[] expectedFull = sign(payload);
        if (presentedSig.length == SCENE_SIG_BYTES) {
            // 紧凑 scene 形态：截断 HMAC 比较 + payload 按大端字节还原 userId。
            byte[] expectedShort = java.util.Arrays.copyOf(expectedFull, SCENE_SIG_BYTES);
            if (!MessageDigest.isEqual(expectedShort, presentedSig)) {
                throw new InvalidRefIdException("ref_id 签名校验失败");
            }
            if (payload.length == 0 || payload.length > 8) {
                throw new InvalidRefIdException("ref_id payload 长度非法");
            }
            return bytesToLong(payload);
        }
        // 标准形态：全 32 字节 HMAC 比较 + payload 按十进制字符串还原 userId。
        // 恒定时间比较，避免计时侧信道。
        if (!MessageDigest.isEqual(expectedFull, presentedSig)) {
            throw new InvalidRefIdException("ref_id 签名校验失败");
        }
        try {
            return Long.parseLong(new String(payload, StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            throw new InvalidRefIdException("ref_id payload 不是合法 userId", e);
        }
    }

    /** userId 编为大端最小字节（去前导 0 字节，至少保留 1 字节）。userId 恒非负。 */
    private static byte[] minimalBigEndian(long v) {
        int n = 1;
        for (int i = 7; i >= 1; i--) {
            if ((v >>> (8 * i)) != 0) {
                n = i + 1;
                break;
            }
        }
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[n - 1 - i] = (byte) (v >>> (8 * i));
        }
        return b;
    }

    /** 大端字节还原为 long（与 {@link #minimalBigEndian} 互逆）。 */
    private static long bytesToLong(byte[] b) {
        long v = 0;
        for (byte x : b) {
            v = (v << 8) | (x & 0xff);
        }
        return v;
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

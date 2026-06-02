package com.sanshuiyuan.cend.wx;

import com.sanshuiyuan.cend.wx.api.dto.JsSdkSignatureResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 微信 JS-SDK 权限验证签名生成（009 T9.3）。
 *
 * <p>按微信规范：将 {@code jsapi_ticket}、{@code noncestr}、{@code timestamp}、{@code url}（去 #hash 的
 * 当前页面完整 URL）按字典序拼成 string1，对其做 SHA1 得到 signature。appId 取公众号 appId。
 *
 * <p>jsapi_ticket 不可用（stub / 未配置真实凭证）时 signature 返回空串，前端据此跳过 {@code wx.config}。
 */
@Service
public class WxJsSdkService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] NONCE_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private final WxJsapiTicketService ticketService;
    private final String appId;

    public WxJsSdkService(WxJsapiTicketService ticketService,
                          @Value("${wxpay.app-id:stub}") String appId) {
        this.ticketService = ticketService;
        this.appId = appId;
    }

    /**
     * 为给定页面 URL 生成 JS-SDK 签名。
     *
     * @param url 调用 {@code wx.config} 的页面完整 URL（前端传入，须与浏览器地址栏去 #hash 后一致）。
     */
    public JsSdkSignatureResponse sign(String url) {
        long timestamp = System.currentTimeMillis() / 1000L;
        String nonceStr = randomNonce();
        String ticket = ticketService.getTicket();

        String signature = "";
        if (ticket != null && !ticket.isBlank() && url != null && !url.isBlank()) {
            signature = buildSignature(ticket, nonceStr, timestamp, url);
        }
        return new JsSdkSignatureResponse(appId, String.valueOf(timestamp), nonceStr, signature);
    }

    /**
     * 按微信规范拼接 string1 并 SHA1。参数按字典序固定 jsapi_ticket/noncestr/timestamp/url 顺序拼接。
     * 包级可见以便单元测试用官方测试向量验证（见 WxJsSdkServiceTest）。
     */
    static String buildSignature(String jsapiTicket, String nonceStr, long timestamp, String url) {
        String string1 = "jsapi_ticket=" + jsapiTicket
                + "&noncestr=" + nonceStr
                + "&timestamp=" + timestamp
                + "&url=" + url;
        return sha1Hex(string1);
    }

    private static String randomNonce() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(NONCE_CHARS[RANDOM.nextInt(NONCE_CHARS.length)]);
        }
        return sb.toString();
    }

    private static String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-1 计算失败", e);
        }
    }
}

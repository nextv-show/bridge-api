package com.sanshuiyuan.logistics.infra.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Webhook 签名验证器。按 provider 配置的共享密钥做 HMAC-SHA256 校验。
 * V1 仅提供骨架；provider 白名单配置在 application.yml。
 */
@Component
public class SignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(SignatureVerifier.class);

    private final Map<String, String> secrets = new ConcurrentHashMap<>();

    public SignatureVerifier(
            @Value("${webhook.shunfeng.secret:}") String shunfengSecret,
            @Value("${webhook.jd.secret:}") String jdSecret) {
        if (shunfengSecret != null && !shunfengSecret.isBlank()) {
            secrets.put("shunfeng", shunfengSecret);
        }
        if (jdSecret != null && !jdSecret.isBlank()) {
            secrets.put("jd", jdSecret);
        }
    }

    /**
     * 判断 provider 是否已配置（未配置直接 404）。
     */
    public boolean isConfigured(String provider) {
        return secrets.containsKey(provider);
    }

    /**
     * 校验签名。签名格式：Header `X-Webhook-Signature: base64(HMAC-SHA256(secret, rawBody))`
     */
    public boolean verify(String provider, String rawBody, String signature) {
        String secret = secrets.get(provider);
        if (secret == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec spec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(spec);
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] actual = Base64.getDecoder().decode(signature);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            log.warn("webhook 签名校验异常 provider={}", provider, e);
            return false;
        }
    }

    /** Constant-time comparison helper. */
    private static final class MessageDigest {
        static boolean isEqual(byte[] a, byte[] b) {
            if (a.length != b.length) return false;
            int result = 0;
            for (int i = 0; i < a.length; i++) {
                result |= a[i] ^ b[i];
            }
            return result == 0;
        }
    }
}

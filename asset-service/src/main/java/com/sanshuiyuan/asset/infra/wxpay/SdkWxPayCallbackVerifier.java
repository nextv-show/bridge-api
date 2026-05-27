package com.sanshuiyuan.asset.infra.wxpay;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;

/**
 * D.2.5: Production WeChat Pay V3 callback verifier. Registered (via {@link WxPayVerifierConfig},
 * guarded by {@code @ConditionalOnProperty wxpay.api-v3-key}) only when the api-v3 key is set, so
 * it is absent in dev/CI where the credential is unset. Uses wechatpay-java 0.2.14
 * {@link RSAAutoCertificateConfig} + {@link NotificationParser} to verify the RSA signature
 * (auto-fetching platform certificates) and AEAD-decrypt the resource into a {@link Transaction}.
 */
public class SdkWxPayCallbackVerifier implements WxPayCallbackVerifier {

    private static final Logger log = LoggerFactory.getLogger(SdkWxPayCallbackVerifier.class);

    private final NotificationParser parser;

    public SdkWxPayCallbackVerifier(String merchantId,
                                    String privateKeyPath,
                                    String merchantSerialNumber,
                                    String apiV3Key,
                                    String publicKeyPath,
                                    String publicKeyId) {
        boolean usePublicKey = publicKeyPath != null && !publicKeyPath.isBlank()
                && publicKeyId != null && !publicKeyId.isBlank();
        NotificationConfig config;
        if (usePublicKey) {
            // 微信支付公钥模式（新商户必须）
            config = new RSAPublicKeyConfig.Builder()
                    .merchantId(merchantId)
                    .privateKeyFromPath(privateKeyPath)
                    .merchantSerialNumber(merchantSerialNumber)
                    .apiV3Key(apiV3Key)
                    .publicKeyFromPath(publicKeyPath)
                    .publicKeyId(publicKeyId)
                    .build();
        } else {
            config = new RSAAutoCertificateConfig.Builder()
                    .merchantId(merchantId)
                    .privateKeyFromPath(privateKeyPath)
                    .merchantSerialNumber(merchantSerialNumber)
                    .apiV3Key(apiV3Key)
                    .build();
        }
        this.parser = new NotificationParser(config);
    }

    @Override
    public VerifiedCallback verify(Map<String, String> headers, String body) {
        Map<String, String> ci = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        ci.putAll(headers);

        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(ci.get("Wechatpay-Serial"))
                .nonce(ci.get("Wechatpay-Nonce"))
                .signature(ci.get("Wechatpay-Signature"))
                .timestamp(ci.get("Wechatpay-Timestamp"))
                .body(body)
                .build();

        try {
            Transaction transaction = parser.parse(requestParam, Transaction.class);
            String outTradeNo = transaction.getOutTradeNo();
            return new VerifiedCallback(transaction.getTransactionId(), Long.valueOf(outTradeNo));
        } catch (WxPaySignatureException e) {
            throw e;
        } catch (Exception e) {
            log.warn("WeChat Pay callback verification/decryption failed: {}", e.getMessage());
            throw new WxPaySignatureException("signature verification failed", e);
        }
    }
}

package com.sanshuiyuan.water.wallet.config;

import com.sanshuiyuan.water.wallet.infra.wxpay.SdkWxPayCallbackVerifier;
import com.sanshuiyuan.water.wallet.infra.wxpay.SdkWxPayClient;
import com.sanshuiyuan.water.wallet.infra.wxpay.StubWxPayClient;
import com.sanshuiyuan.water.wallet.infra.wxpay.UnconfiguredWxPayCallbackVerifier;
import com.sanshuiyuan.water.wallet.infra.wxpay.WxPayCallbackVerifier;
import com.sanshuiyuan.water.wallet.infra.wxpay.WxPayClient;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 微信支付 V3 装配中枢（钱包充值）。配齐真实凭证（mp-app-id / mch-id / api-v3-key / 商户私钥 / 序列号 / notify-url）
 * 时启用 SDK 实现，否则回退 stub client 与 unconfigured 验签器（安全失败）。
 * 商户私钥 / APIv3 Key 仅此处加载，绝不下发前端。
 */
@Configuration
public class WxPayConfig {

    private static final Logger log = LoggerFactory.getLogger(WxPayConfig.class);

    @Value("${wxpay.mp-app-id:stub}") private String mpAppId;
    @Value("${wxpay.mch-id:stub}") private String mchId;
    @Value("${wxpay.api-v3-key:stub}") private String apiV3Key;
    @Value("${wxpay.private-key-path:}") private String privateKeyPath;
    @Value("${wxpay.merchant-serial-number:}") private String merchantSerialNumber;
    @Value("${wxpay.notify-url:}") private String notifyUrl;
    @Value("${wxpay.public-key-path:}") private String publicKeyPath;
    @Value("${wxpay.public-key-id:}") private String publicKeyId;

    private Config cachedConfig;

    private boolean isConfigured() {
        return notBlankNotStub(mpAppId)
                && notBlankNotStub(mchId)
                && notBlankNotStub(apiV3Key)
                && privateKeyPath != null && !privateKeyPath.isBlank()
                && merchantSerialNumber != null && !merchantSerialNumber.isBlank()
                && notifyUrl != null && !notifyUrl.isBlank();
    }

    private static boolean notBlankNotStub(String v) {
        return v != null && !v.isBlank() && !"stub".equals(v);
    }

    /**
     * 懒构建并复用。优先使用“微信支付公钥”模式（新商户必须）；
     * 若未配置 publicKeyPath/publicKeyId，回退旧“平台证书自动下载”模式。
     */
    private synchronized Config coreConfig() {
        if (cachedConfig == null) {
            boolean usePublicKey = publicKeyPath != null && !publicKeyPath.isBlank()
                    && publicKeyId != null && !publicKeyId.isBlank();
            if (usePublicKey) {
                log.info("微信支付使用公钥模式（RSAPublicKeyConfig）publicKeyId={}", publicKeyId);
                cachedConfig = new RSAPublicKeyConfig.Builder()
                        .merchantId(mchId)
                        .privateKeyFromPath(privateKeyPath)
                        .merchantSerialNumber(merchantSerialNumber)
                        .apiV3Key(apiV3Key)
                        .publicKeyFromPath(publicKeyPath)
                        .publicKeyId(publicKeyId)
                        .build();
            } else {
                log.info("微信支付使用平台证书模式（RSAAutoCertificateConfig）");
                cachedConfig = new RSAAutoCertificateConfig.Builder()
                        .merchantId(mchId)
                        .privateKeyFromPath(privateKeyPath)
                        .merchantSerialNumber(merchantSerialNumber)
                        .apiV3Key(apiV3Key)
                        .build();
            }
        }
        return cachedConfig;
    }

    @Bean
    public WxPayClient wxPayClient() {
        if (!isConfigured()) {
            log.warn("微信支付未配置，回退 StubWxPayClient");
            return new StubWxPayClient();
        }
        try {
            JsapiServiceExtension jsapi = new JsapiServiceExtension.Builder().config(coreConfig()).build();
            log.info("启用微信支付 V3 JSAPI（SdkWxPayClient）mpAppId={}", mpAppId);
            return new SdkWxPayClient(jsapi, mpAppId, mchId, notifyUrl);
        } catch (Exception e) {
            log.error("微信支付 SDK 初始化失败，回退 StubWxPayClient（凭证或网络问题）: {}", e.getMessage());
            cachedConfig = null;
            return new StubWxPayClient();
        }
    }

    @Bean
    public WxPayCallbackVerifier wxPayCallbackVerifier() {
        if (!isConfigured()) {
            return new UnconfiguredWxPayCallbackVerifier();
        }
        try {
            return new SdkWxPayCallbackVerifier(new NotificationParser((NotificationConfig) coreConfig()));
        } catch (Exception e) {
            log.error("微信支付验签器初始化失败，回退 UnconfiguredWxPayCallbackVerifier: {}", e.getMessage());
            return new UnconfiguredWxPayCallbackVerifier();
        }
    }
}

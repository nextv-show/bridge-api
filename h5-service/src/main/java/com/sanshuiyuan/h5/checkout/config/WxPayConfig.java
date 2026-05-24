package com.sanshuiyuan.h5.checkout.config;

import com.sanshuiyuan.h5.checkout.infra.wxpay.SdkWxPayCallbackVerifier;
import com.sanshuiyuan.h5.checkout.infra.wxpay.SdkWxPayClient;
import com.sanshuiyuan.h5.checkout.infra.wxpay.SdkWxRefundClient;
import com.sanshuiyuan.h5.checkout.infra.wxpay.StubWxPayClient;
import com.sanshuiyuan.h5.checkout.infra.wxpay.StubWxRefundClient;
import com.sanshuiyuan.h5.checkout.infra.wxpay.UnconfiguredWxPayCallbackVerifier;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxPayCallbackVerifier;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxPayClient;
import com.sanshuiyuan.h5.checkout.infra.wxpay.WxRefundClient;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 微信支付 V3 装配中枢。配齐真实凭证（mch-id / api-v3-key / 商户私钥 / 序列号）时启用 SDK 实现，
 * 否则回退 stub（pay/refund）与 unconfigured 验签器（安全失败）。
 * 商户私钥 / APIv3 Key 仅此处加载，绝不下发前端。
 */
@Configuration
public class WxPayConfig {

    private static final Logger log = LoggerFactory.getLogger(WxPayConfig.class);

    @Value("${wxpay.app-id:stub}") private String appId;
    @Value("${wxpay.mch-id:stub}") private String mchId;
    @Value("${wxpay.api-v3-key:stub}") private String apiV3Key;
    @Value("${wxpay.private-key-path:}") private String privateKeyPath;
    @Value("${wxpay.merchant-serial-number:}") private String merchantSerialNumber;
    @Value("${wxpay.notify-url:}") private String notifyUrl;
    @Value("${wxpay.refund-notify-url:}") private String refundNotifyUrl;

    private RSAAutoCertificateConfig cachedConfig;

    private boolean isConfigured() {
        return notBlankNotStub(mchId)
                && notBlankNotStub(apiV3Key)
                && privateKeyPath != null && !privateKeyPath.isBlank()
                && merchantSerialNumber != null && !merchantSerialNumber.isBlank();
    }

    private static boolean notBlankNotStub(String v) {
        return v != null && !v.isBlank() && !"stub".equals(v);
    }

    /**
     * 懒构建并复用（含平台证书自动下载）。build() 会联网拉取微信平台证书；
     * 若失败抛 RuntimeException，由调用方 @Bean 方法 catch 并降级 stub。
     */
    private synchronized RSAAutoCertificateConfig coreConfig() {
        if (cachedConfig == null) {
            cachedConfig = new RSAAutoCertificateConfig.Builder()
                    .merchantId(mchId)
                    .privateKeyFromPath(privateKeyPath)
                    .merchantSerialNumber(merchantSerialNumber)
                    .apiV3Key(apiV3Key)
                    .build();
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
            log.info("启用微信支付 V3 JSAPI（SdkWxPayClient）mchId={}", mchId);
            return new SdkWxPayClient(jsapi, appId, mchId, notifyUrl);
        } catch (Exception e) {
            log.error("微信支付 SDK 初始化失败，回退 StubWxPayClient（凭证或网络问题）: {}", e.getMessage());
            cachedConfig = null;
            return new StubWxPayClient();
        }
    }

    @Bean
    public WxRefundClient wxRefundClient() {
        if (!isConfigured()) {
            return new StubWxRefundClient();
        }
        try {
            var sdkRefund = new com.wechat.pay.java.service.refund.RefundService.Builder()
                    .config(coreConfig()).build();
            NotificationParser parser = new NotificationParser(coreConfig());
            return new SdkWxRefundClient(sdkRefund, parser, refundNotifyUrl);
        } catch (Exception e) {
            log.error("微信退款 SDK 初始化失败，回退 StubWxRefundClient: {}", e.getMessage());
            return new StubWxRefundClient();
        }
    }

    @Bean
    public WxPayCallbackVerifier wxPayCallbackVerifier() {
        if (!isConfigured()) {
            return new UnconfiguredWxPayCallbackVerifier();
        }
        try {
            return new SdkWxPayCallbackVerifier(new NotificationParser(coreConfig()));
        } catch (Exception e) {
            log.error("微信支付验签器初始化失败，回退 UnconfiguredWxPayCallbackVerifier: {}", e.getMessage());
            return new UnconfiguredWxPayCallbackVerifier();
        }
    }
}

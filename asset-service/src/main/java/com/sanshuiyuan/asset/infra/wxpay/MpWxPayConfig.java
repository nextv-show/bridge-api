package com.sanshuiyuan.asset.infra.wxpay;

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
 * 小程序水费充值的微信支付装配。配齐凭证（商户号/私钥/序列号/APIv3 Key/小程序 appid/回调地址）
 * 时启用 SDK 实现，否则回退 stub / 失败关闭验签器。商户私钥与 APIv3 Key 仅此处加载。
 *
 * <p>优先「微信支付公钥模式」（RSAPublicKeyConfig，新商户必须）：配置 public-key-path + public-key-id
 * 时启用；否则回退旧「平台证书自动下载模式」（RSAAutoCertificateConfig）。与 h5-service 一致。
 */
@Configuration
public class MpWxPayConfig {

    private static final Logger log = LoggerFactory.getLogger(MpWxPayConfig.class);

    @Value("${wxpay.merchant-id:}") private String merchantId;
    @Value("${wxpay.api-key-path:}") private String privateKeyPath;
    @Value("${wxpay.merchant-serial-number:}") private String merchantSerialNumber;
    @Value("${wxpay.api-v3-key:}") private String apiV3Key;
    @Value("${wxpay.mp-app-id:}") private String mpAppId;
    @Value("${wxpay.wallet-notify-url:}") private String walletNotifyUrl;
    @Value("${wxpay.public-key-path:}") private String publicKeyPath;
    @Value("${wxpay.public-key-id:}") private String publicKeyId;
    @Value("${wxpay.refund-notify-url:}") private String refundNotifyUrl;

    private Config cachedConfig;

    private boolean credsPresent() {
        return nb(merchantId) && nb(privateKeyPath) && nb(merchantSerialNumber) && nb(apiV3Key);
    }

    private static boolean nb(String v) {
        return v != null && !v.isBlank() && !"stub".equals(v);
    }

    /** 懒构建并复用核心配置。公钥模式优先，回退平台证书自动下载。 */
    private synchronized Config coreConfig() {
        if (cachedConfig == null) {
            boolean usePublicKey = nb(publicKeyPath) && nb(publicKeyId);
            if (usePublicKey) {
                log.info("小程序微信支付使用公钥模式（RSAPublicKeyConfig）publicKeyId={}", publicKeyId);
                cachedConfig = new RSAPublicKeyConfig.Builder()
                        .merchantId(merchantId)
                        .privateKeyFromPath(privateKeyPath)
                        .merchantSerialNumber(merchantSerialNumber)
                        .apiV3Key(apiV3Key)
                        .publicKeyFromPath(publicKeyPath)
                        .publicKeyId(publicKeyId)
                        .build();
            } else {
                log.info("小程序微信支付使用平台证书模式（RSAAutoCertificateConfig）");
                cachedConfig = new RSAAutoCertificateConfig.Builder()
                        .merchantId(merchantId)
                        .privateKeyFromPath(privateKeyPath)
                        .merchantSerialNumber(merchantSerialNumber)
                        .apiV3Key(apiV3Key)
                        .build();
            }
        }
        return cachedConfig;
    }

    @Bean
    public MpWxPayClient mpWxPayClient() {
        if (!credsPresent() || !nb(mpAppId) || !nb(walletNotifyUrl)) {
            log.warn("小程序微信支付未配置（缺商户凭证/小程序 appid/回调地址），回退 StubMpWxPayClient");
            return new StubMpWxPayClient();
        }
        try {
            JsapiServiceExtension jsapi = new JsapiServiceExtension.Builder().config(coreConfig()).build();
            log.info("启用小程序微信支付 JSAPI（SdkMpWxPayClient）mchId={} mpAppId={}", merchantId, mpAppId);
            return new SdkMpWxPayClient(jsapi, mpAppId, merchantId, walletNotifyUrl);
        } catch (Exception e) {
            log.error("小程序微信支付 SDK 初始化失败，回退 Stub: {}", e.getMessage());
            cachedConfig = null;
            return new StubMpWxPayClient();
        }
    }

    /**
     * 购机退款客户端。配齐凭证时启用 SDK（wechatpay-java RefundService + NotificationParser），
     * 否则回退 Stub —— 与 {@link #mpWxPayClient()} 同形（dev/CI 不打微信）。
     */
    @Bean
    public WxRefundClient wxRefundClient() {
        if (!credsPresent()) {
            log.warn("微信退款未配置（缺商户凭证），回退 StubWxRefundClient");
            return new StubWxRefundClient();
        }
        try {
            com.wechat.pay.java.service.refund.RefundService refundService =
                    new com.wechat.pay.java.service.refund.RefundService.Builder().config(coreConfig()).build();
            NotificationParser parser = new NotificationParser((NotificationConfig) coreConfig());
            log.info("启用微信退款（SdkWxRefundClient）mchId={}", merchantId);
            return new SdkWxRefundClient(refundService, parser, refundNotifyUrl);
        } catch (Exception e) {
            log.error("微信退款 SDK 初始化失败，回退 Stub: {}", e.getMessage());
            cachedConfig = null;
            return new StubWxRefundClient();
        }
    }

    @Bean
    public WalletPayCallbackVerifier walletPayCallbackVerifier() {
        if (!credsPresent()) {
            return new UnconfiguredWalletPayCallbackVerifier();
        }
        try {
            return new SdkWalletPayCallbackVerifier(new NotificationParser((NotificationConfig) coreConfig()));
        } catch (Exception e) {
            log.error("钱包充值回调验签器初始化失败，回退 Unconfigured: {}", e.getMessage());
            return new UnconfiguredWalletPayCallbackVerifier();
        }
    }
}

package com.sanshuiyuan.asset.infra.wxpay;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 小程序水费充值的微信支付装配。配齐凭证（商户号/私钥/序列号/APIv3 Key/小程序 appid/回调地址）
 * 时启用 SDK 实现，否则回退 stub / 失败关闭验签器。商户私钥与 APIv3 Key 仅此处加载。
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

    private boolean credsPresent() {
        return nb(merchantId) && nb(privateKeyPath) && nb(merchantSerialNumber) && nb(apiV3Key);
    }

    private static boolean nb(String v) {
        return v != null && !v.isBlank() && !"stub".equals(v);
    }

    @Bean
    public MpWxPayClient mpWxPayClient() {
        if (!credsPresent() || !nb(mpAppId) || !nb(walletNotifyUrl)) {
            log.warn("小程序微信支付未配置（缺商户凭证/小程序 appid/回调地址），回退 StubMpWxPayClient");
            return new StubMpWxPayClient();
        }
        try {
            RSAAutoCertificateConfig config = new RSAAutoCertificateConfig.Builder()
                    .merchantId(merchantId)
                    .privateKeyFromPath(privateKeyPath)
                    .merchantSerialNumber(merchantSerialNumber)
                    .apiV3Key(apiV3Key)
                    .build();
            JsapiServiceExtension jsapi = new JsapiServiceExtension.Builder().config(config).build();
            log.info("启用小程序微信支付 JSAPI（SdkMpWxPayClient）mchId={} mpAppId={}", merchantId, mpAppId);
            return new SdkMpWxPayClient(jsapi, mpAppId, merchantId, walletNotifyUrl);
        } catch (Exception e) {
            log.error("小程序微信支付 SDK 初始化失败，回退 Stub: {}", e.getMessage());
            return new StubMpWxPayClient();
        }
    }

    @Bean
    public WalletPayCallbackVerifier walletPayCallbackVerifier() {
        if (!credsPresent()) {
            return new UnconfiguredWalletPayCallbackVerifier();
        }
        try {
            return new SdkWalletPayCallbackVerifier(merchantId, privateKeyPath, merchantSerialNumber, apiV3Key);
        } catch (Exception e) {
            log.error("钱包充值回调验签器初始化失败，回退 Unconfigured: {}", e.getMessage());
            return new UnconfiguredWalletPayCallbackVerifier();
        }
    }
}

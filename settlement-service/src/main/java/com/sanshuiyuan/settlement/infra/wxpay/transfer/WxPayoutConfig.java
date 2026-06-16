package com.sanshuiyuan.settlement.infra.wxpay.transfer;

import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.http.DefaultHttpClientBuilder;
import com.wechat.pay.java.core.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 「商家转账」客户端装配（公钥模式，复用 cend/water 同 MCH 凭证）。
 * 配齐凭证（mch-id / api-v3-key / 商户私钥 / 序列号 / 微信支付公钥 + 公钥ID）则启用真实 SDK 客户端，
 * 否则回退 {@link StubWxTransferBillsClient}（不动真钱，dev / 未配置环境可启动）。
 * 商户私钥 / APIv3 Key 仅此处加载，绝不下发前端。
 */
@Configuration
public class WxPayoutConfig {

    private static final Logger log = LoggerFactory.getLogger(WxPayoutConfig.class);

    @Value("${wxpay.mp-app-id:stub}") private String mpAppId;
    @Value("${wxpay.mch-id:stub}") private String mchId;
    @Value("${wxpay.api-v3-key:stub}") private String apiV3Key;
    @Value("${wxpay.merchant-serial-number:}") private String merchantSerialNumber;
    @Value("${wxpay.private-key-path:}") private String privateKeyPath;
    @Value("${wxpay.public-key-path:}") private String publicKeyPath;
    @Value("${wxpay.public-key-id:}") private String publicKeyId;
    @Value("${wxpay.payout.transfer-scene-id:1005}") private String transferSceneId;
    @Value("${wxpay.payout.report-post-type:水机运营服务商}") private String reportPostType;
    @Value("${wxpay.payout.report-remark:水机运营服务分成}") private String reportRemark;
    @Value("${wxpay.payout.notify-url:}") private String notifyUrl;

    private boolean isConfigured() {
        return notBlankNotStub(mpAppId) && notBlankNotStub(mchId) && notBlankNotStub(apiV3Key)
                && hasText(merchantSerialNumber) && hasText(privateKeyPath)
                && hasText(publicKeyPath) && hasText(publicKeyId);
    }

    private static boolean hasText(String v) { return v != null && !v.isBlank(); }
    private static boolean notBlankNotStub(String v) { return hasText(v) && !"stub".equals(v); }

    @Bean
    public WxTransferBillsClient wxTransferBillsClient() {
        if (!isConfigured()) {
            log.warn("商家转账未配置（缺 mch/api-v3-key/私钥/序列号/公钥），回退 StubWxTransferBillsClient（不动真钱）");
            return new StubWxTransferBillsClient();
        }
        try {
            Config config = new RSAPublicKeyConfig.Builder()
                    .merchantId(mchId)
                    .privateKeyFromPath(privateKeyPath)
                    .merchantSerialNumber(merchantSerialNumber)
                    .apiV3Key(apiV3Key)
                    .publicKeyFromPath(publicKeyPath)
                    .publicKeyId(publicKeyId)
                    .build();
            HttpClient httpClient = new DefaultHttpClientBuilder().config(config).build();
            log.info("启用商家转账 V3（SdkWxTransferBillsClient，公钥模式）mchId={} appId={} sceneId={}",
                    mchId, mpAppId, transferSceneId);
            return new SdkWxTransferBillsClient(httpClient, mpAppId, transferSceneId,
                    reportPostType, reportRemark, notifyUrl);
        } catch (Exception e) {
            log.error("商家转账 SDK 初始化失败，回退 StubWxTransferBillsClient: {}", e.getMessage());
            return new StubWxTransferBillsClient();
        }
    }
}

package com.sanshuiyuan.h5.checkout.infra.aliyun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class StubAliyunKycClient implements AliyunKycClient {

    private static final Logger log = LoggerFactory.getLogger(StubAliyunKycClient.class);

    @Override
    public KycInitResult init(String openid, String metaInfo, String returnUrl) {
        String certifyId = "stub-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[stub] KYC init for openid=*** certifyId={}", certifyId);
        // 模拟阿里云回跳格式：?response=<json> 拼在真实 query（hash 路由也是如此），
        // 让前端走与真实环境一致的解析路径，本地即可联调回跳→查询闭环。
        String json = "{\"code\":\"1000\",\"subCode\":\"Z5050\",\"extInfo\":{\"certifyId\":\"" + certifyId + "\"}}";
        String responseParam = "response=" + URLEncoder.encode(json, StandardCharsets.UTF_8);
        String url;
        if (returnUrl == null) {
            url = "https://stub-verify.example.com/?" + responseParam;
        } else {
            int hashIdx = returnUrl.indexOf('#');
            url = hashIdx >= 0
                    ? returnUrl.substring(0, hashIdx) + "?" + responseParam + returnUrl.substring(hashIdx)
                    : returnUrl + (returnUrl.contains("?") ? "&" : "?") + responseParam;
        }
        return new KycInitResult(certifyId, "stub-token", url);
    }

    @Override
    public KycVerifyResult queryResult(String certifyId) {
        log.info("[stub] KYC queryResult certifyId={}", certifyId);
        return new KycVerifyResult(true, "张三", "340123199001011234");
    }
}

package com.sanshuiyuan.ess.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 腾讯电子签企业版 API 配置属性。
 * <p>
 * 所有密钥、企业信息、模板 ID 等均通过 application.yml 外部化配置，禁止硬编码。
 */
@Validated
@ConfigurationProperties(prefix = "ess")
public record EssProperties(
        @NotBlank(message = "ESS secret-id 必须配置")
        String secretId,

        @NotBlank(message = "ESS secret-key 必须配置")
        String secretKey,

        @NotBlank(message = "ESS operator-id 必须配置")
        String operatorId,

        @NotBlank(message = "ESS corp-id 必须配置")
        String corpId,

        /** 合同模板 ID，可逗号分隔多个 */
        String templateId,

        /** Webhook 回调地址 */
        String callbackUrl,

        /** API 地域，默认 ap-guangzhou */
        String apiRegion,

        /** API 接入点，默认 ess.test.ess.tencent.cn（联调环境） */
        String apiEndpoint,

        /** HTTP 连接超时（毫秒），默认 5000 */
        @Positive
        Integer connectTimeoutMs,

        /** HTTP 读取超时（毫秒），默认 10000 */
        @Positive
        Integer readTimeoutMs,

        /** API 调用最大重试次数，默认 3 */
        @Positive
        Integer maxRetries,

        /** 是否在创建签署方时传身份证号，生产环境建议开启 */
        Boolean collectIdCard
) {

    /**
     * 兼容 record 构造：提供默认值。
     */
    public EssProperties {
        if (apiRegion == null || apiRegion.isBlank()) {
            apiRegion = "ap-guangzhou";
        }
        if (apiEndpoint == null || apiEndpoint.isBlank()) {
            apiEndpoint = "ess.test.ess.tencent.cn";
        }
        if (connectTimeoutMs == null) {
            connectTimeoutMs = 5000;
        }
        if (readTimeoutMs == null) {
            readTimeoutMs = 10000;
        }
        if (maxRetries == null) {
            maxRetries = 3;
        }
        if (collectIdCard == null) {
            collectIdCard = Boolean.FALSE;
        }
    }
}

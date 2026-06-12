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
        Boolean collectIdCard,

        /**
         * 个人签署方核身方式（ApproverSignTypes），逗号分隔的整数。
         * <p>腾讯电子签取值：1=人脸，2=密码，3=运营商三要素。小程序认购走「短信短链」签署，
         * 必须避开人脸（1），默认 {@code 3}（运营商三要素，姓名+手机+身份证三要素核验，配合签署时短信验证码意愿认证）。
         * <p>留空则不下发该参数、沿用腾讯电子签账号侧签署要求。
         * <p>⚠️ 联调验证点：确认账号侧未强制刷脸、且 ApproverSignTypes=[3] 能生效（不同版本参数名/取值可能不同）。
         */
        String approverSignTypes
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
        if (approverSignTypes == null) {
            approverSignTypes = "3";
        }
    }

    /**
     * 解析 {@link #approverSignTypes} 为 int 列表；留空/全非法时返回空列表（不下发该参数）。
     */
    public java.util.List<Integer> approverSignTypeList() {
        if (approverSignTypes == null || approverSignTypes.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (String part : approverSignTypes.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                try {
                    out.add(Integer.valueOf(t));
                } catch (NumberFormatException ignore) {
                    // 跳过非法片段
                }
            }
        }
        return out;
    }
}

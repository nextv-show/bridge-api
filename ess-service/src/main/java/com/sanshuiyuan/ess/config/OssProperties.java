package com.sanshuiyuan.ess.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 对象存储配置属性。
 * <p>
 * 用于合同 PDF 的双冗余存储（腾讯云端 + 自有 OSS）。
 */
@Validated
@ConfigurationProperties(prefix = "oss")
public record OssProperties(

    /** OSS endpoint */
    String endpoint,

    /** Access Key ID */
    String accessKeyId,

    /** Access Key Secret */
    String accessKeySecret,

    /** 存储桶名称 */
    String bucketName,

    /** 合同文件存储路径前缀 */
    String contractPathPrefix
) {
    public OssProperties {
        if (contractPathPrefix == null || contractPathPrefix.isBlank()) {
            contractPathPrefix = "contracts/";
        }
    }
}

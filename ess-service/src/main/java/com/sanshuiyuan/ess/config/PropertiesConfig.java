package com.sanshuiyuan.ess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 属性配置注册。
 * <p>
 * 集中注册所有 @ConfigurationProperties record 类型。
 */
@Configuration
@EnableConfigurationProperties({
        EssProperties.class,
        EssFileProperties.class,
        ContractPdfProperties.class,
        OssProperties.class
})
public class PropertiesConfig {
}

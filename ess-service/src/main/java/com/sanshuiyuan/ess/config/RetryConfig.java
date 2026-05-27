package com.sanshuiyuan.ess.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 启用 Spring Retry 支持。
 * <p>
 * 配合 @Retryable 注解实现 API 调用的指数退避重试。
 */
@Configuration
@EnableRetry
public class RetryConfig {
}

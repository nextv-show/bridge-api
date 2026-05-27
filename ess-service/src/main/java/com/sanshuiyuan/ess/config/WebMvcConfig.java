package com.sanshuiyuan.ess.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 * <p>
 * 注册客户端类型识别拦截器，应用于所有 /api/h5/** 和 /api/ess/** 路径。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ClientTypeInterceptor clientTypeInterceptor;

    public WebMvcConfig(ClientTypeInterceptor clientTypeInterceptor) {
        this.clientTypeInterceptor = clientTypeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(clientTypeInterceptor)
                .addPathPatterns("/api/h5/**", "/api/ess/**")
                .excludePathPatterns("/api-docs/**", "/actuator/**");
    }
}

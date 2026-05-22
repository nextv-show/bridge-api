package com.sanshuiyuan.h5.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * H5 跨域占位。前端经 CDN/Nginx 同源反代时无需 CORS；本地分端口联调（vite :5173 → :8083）时放行。
 * 公开只读 GET，允许常见来源；生产以 Nginx 同源为准，可按需收紧 allowedOrigins。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/h5/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}

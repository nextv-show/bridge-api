package com.sanshuiyuan.cend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc OpenAPI 配置。Swagger UI 默认在 /swagger-ui/index.html，JSON 在 /api-docs。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI h5ServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("三水元 H5 Service")
                .description("微信 H5 营销闭环后端：P1 落地页配置只读接口（102），103/104/105 复用脚手架。")
                .version("0.1.0"));
    }
}

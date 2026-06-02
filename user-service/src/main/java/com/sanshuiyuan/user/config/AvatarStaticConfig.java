package com.sanshuiyuan.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将上传的头像目录（{@code app.avatar.dir}）以 {@code /avatars/**} 暴露为静态资源。
 *
 * <p>生产由 nginx 直接服务 {@code /www/avatars}，请求不会进 Spring；本配置是回退兜底，
 * 主要让本地开发（头像存到可写目录）也能通过 {@code app.avatar.base-url}/avatars/xxx 取回图片。
 */
@Configuration
public class AvatarStaticConfig implements WebMvcConfigurer {

    @Value("${app.avatar.dir:/www/avatars}")
    private String avatarDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = avatarDir.endsWith("/") ? avatarDir : avatarDir + "/";
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("file:" + location);
    }
}

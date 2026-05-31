package com.sanshuiyuan.asset.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           @Value("${jwt.secret}") String jwtSecret,
                                           @Value("${s2s-token:local-dev-static-token}") String s2sToken) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api-docs/**", "/swagger-ui/**", "/wxpay/callback", "/wxpay/wallet-callback").permitAll()
                // 内部 S2S 接口在 authorize 链上放行，鉴权改由 S2sTokenFilter 按 X-S2S-Token 校验。
                .requestMatchers("/internal/**").permitAll()
                .anyRequest().authenticated()
            )
            // 验签通过才信任 sub —— 修复此前不验签可被伪造冒充的越权漏洞（见 JwtBearerFilter）。
            .addFilterBefore(new JwtBearerFilter(jwtSecret), UsernamePasswordAuthenticationFilter.class)
            // 入站 S2S 鉴权：/internal/ 需 X-S2S-Token，置于 JWT 过滤器之前先行短路非法内部调用。
            .addFilterBefore(new S2sTokenFilter(s2sToken), JwtBearerFilter.class);
        return http.build();
    }
}

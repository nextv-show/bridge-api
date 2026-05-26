package com.sanshuiyuan.h5.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.h5.auth.H5JwtFilter;
import com.sanshuiyuan.h5.auth.H5JwtService;
import com.sanshuiyuan.h5.common.ApiResponse;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, H5JwtService jwtService, ObjectMapper objectMapper) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // CORS 预检放行
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 公开只读 / 第三方回调 / 登录：无需 H5 JWT
                .requestMatchers(
                    "/api/h5/landing/**",
                    "/api/h5/specs",
                    "/api/h5/auth/**",
                    // 微信 JS-SDK 签名 / 分享文案：公开只读（009）。注意 /api/h5/referral/** 其余仍需登录态。
                    "/api/h5/wx/**",
                    // 邀请确认页解析推荐人脱敏资料：公开只读（014），仅脱敏昵称+头像，零可定位 PII。
                    "/api/h5/referral/resolve-inviter",
                    "/api/h5/pay/callback",
                    "/api/h5/pay/refund-callback",
                    "/api/h5/pay/simulate-callback",
                    "/api/h5/pay/simulate-refund-callback",
                    "/api-docs/**",
                    "/swagger-ui/**",
                    "/actuator/**"
                ).permitAll()
                // 其余（KYC / 下单 / pay/jsapi / 订单详情 / 退款申请 / 发票）需登录态
                .anyRequest().authenticated()
            )
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> {
                    // 返回 JSON 体而非空响应，避免前端 res.json() 因空 body 抛 "Unexpected end of JSON input"
                    res.setStatus(ErrorCode.UNAUTHORIZED.httpStatus().value());
                    res.setContentType("application/json;charset=UTF-8");
                    objectMapper.writeValue(res.getWriter(), ApiResponse.error(ErrorCode.UNAUTHORIZED));
                }))
            .addFilterBefore(new H5JwtFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

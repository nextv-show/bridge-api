package com.sanshuiyuan.logistics.config;

import com.sanshuiyuan.logistics.auth.H5JwtFilter;
import com.sanshuiyuan.logistics.auth.H5JwtService;
import com.sanshuiyuan.logistics.auth.S2sTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Phase D 鉴权：
 * - /actuator/** & /api-docs/** 放行；
 * - /logistics/webhook/** 放行（签名在校验层做）；
 * - /logistics/ops/** 要求 S2S token（005 上线前人工推进）；
 * - 其余 /logistics/** 要求 H5 JWT（C 端用户）。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public H5JwtService h5JwtService(@Value("${h5.jwt-secret}") String secret) {
        return new H5JwtService(secret);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           H5JwtService jwtService,
                                           @Value("${s2s.token:dev-s2s-shared-token}") String s2sToken) throws Exception {
        http
            .csrf(c -> c.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/**", "/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers("/logistics/webhook/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new H5JwtFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new S2sTokenFilter(s2sToken), H5JwtFilter.class);
        return http.build();
    }
}

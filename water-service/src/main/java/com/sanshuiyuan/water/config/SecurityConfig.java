package com.sanshuiyuan.water.config;

import com.sanshuiyuan.water.auth.H5JwtFilter;
import com.sanshuiyuan.water.auth.H5JwtService;
import com.sanshuiyuan.water.auth.S2sTokenFilter;
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
 * 鉴权：C 端路径 /api/w/** 用 H5 JWT；/internal/** 用 S2S shared token；
 * /api/w/public/** 与 actuator/api-docs 放行。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public H5JwtService h5JwtService(@Value("${h5.jwt-secret}") String secret) {
        return new H5JwtService(secret);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, H5JwtService jwtService,
                                           @Value("${s2s.token:dev-s2s-shared-token}") String s2sToken) throws Exception {
        http
            .csrf(c -> c.disable())
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/w/public/**").permitAll()
                .requestMatchers("/api/w/wallet/topup/callback").permitAll()
                .requestMatchers("/actuator/**", "/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new H5JwtFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new S2sTokenFilter(s2sToken), H5JwtFilter.class);
        return http.build();
    }
}

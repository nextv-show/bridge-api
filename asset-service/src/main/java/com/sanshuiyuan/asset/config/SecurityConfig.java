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
                                           @Value("${jwt.secret}") String jwtSecret) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api-docs/**", "/swagger-ui/**", "/wxpay/callback", "/wxpay/wallet-callback").permitAll()
                .anyRequest().authenticated()
            )
            // 验签通过才信任 sub —— 修复此前不验签可被伪造冒充的越权漏洞（见 JwtBearerFilter）。
            .addFilterBefore(new JwtBearerFilter(jwtSecret), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

package com.sanshuiyuan.matching.config;

import com.sanshuiyuan.matching.auth.H5JwtFilter;
import com.sanshuiyuan.matching.auth.H5JwtService;
import org.springframework.beans.factory.annotation.Value;
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
    public H5JwtService h5JwtService(@Value("${h5.jwt-secret}") String secret) {
        return new H5JwtService(secret);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, H5JwtService jwtService) throws Exception {
        http
            .csrf(c -> c.disable())
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/**", "/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new H5JwtFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

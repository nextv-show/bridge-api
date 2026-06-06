package com.sanshuiyuan.settlement.config;

import com.sanshuiyuan.settlement.auth.H5JwtFilter;
import com.sanshuiyuan.settlement.auth.H5JwtService;
import com.sanshuiyuan.settlement.auth.S2sTokenFilter;
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
    public SecurityFilterChain filterChain(HttpSecurity http, H5JwtService jwtService,
                                           @Value("${s2s.token:dev-s2s-shared-token}") String s2sToken) throws Exception {
        http
            .csrf(c -> c.disable())
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/s/payout/callback").permitAll()
                .requestMatchers("/actuator/**", "/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new H5JwtFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new S2sTokenFilter(s2sToken), H5JwtFilter.class);
        return http.build();
    }
}

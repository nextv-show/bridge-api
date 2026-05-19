package com.sanshuiyuan.user.config;

import com.sanshuiyuan.user.infra.jwt.JwtIssuer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtIssuer jwtIssuer;

    @Value("${s2s-token:internal-service-token}")
    private String s2sToken;

    public SecurityConfig(JwtIssuer jwtIssuer) {
        this.jwtIssuer = jwtIssuer;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/wxpay/callback").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/internal/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtIssuer), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new S2sTokenFilter(s2sToken), JwtAuthenticationFilter.class);

        return http.build();
    }

    private static class JwtAuthenticationFilter extends OncePerRequestFilter {

        private final JwtIssuer jwtIssuer;

        JwtAuthenticationFilter(JwtIssuer jwtIssuer) {
            this.jwtIssuer = jwtIssuer;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                try {
                    var claims = jwtIssuer.parseToken(token);
                    if ("access".equals(claims.get("type"))) {
                        Long userId = Long.valueOf((String) claims.get("sub"));
                        String role = (String) claims.get("role");
                        var auth = new UsernamePasswordAuthenticationToken(
                                userId, null,
                                role != null ? List.of(new SimpleGrantedAuthority("ROLE_" + role)) : List.of()
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } catch (Exception ignored) {
                }
            }
            filterChain.doFilter(request, response);
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String path = request.getRequestURI();
            return path.startsWith("/auth/") || path.startsWith("/actuator") ||
                   path.startsWith("/api-docs") || path.startsWith("/swagger-ui") ||
                   path.equals("/swagger-ui.html");
        }
    }

    private static class S2sTokenFilter extends OncePerRequestFilter {

        private final String expectedToken;

        S2sTokenFilter(String expectedToken) {
            this.expectedToken = expectedToken;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String path = request.getRequestURI();
            if (path.startsWith("/internal/")) {
                String token = request.getHeader("X-S2S-Token");
                if (token == null || !token.equals(expectedToken)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}

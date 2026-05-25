package com.sanshuiyuan.admin.config;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${admin.jwt-secret}")
    private String jwtSecret;

    @Value("${admin.jwt-ttl-hours}")
    private int jwtTtlHours;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AdminJwtUtil jwtUtil() {
        return new AdminJwtUtil(jwtSecret, jwtTtlHours);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.disable())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(
                org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api-docs/**", "/swagger-ui/**",
                    "/admin/auth/login").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> res.sendError(401, "Unauthorized"))
                .accessDeniedHandler((req, res, ex) -> res.sendError(403, "Forbidden"))
            )
            .addFilterBefore(new AdminJwtFilter(jwtSecret),
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    public static class AdminJwtUtil {
        private final byte[] secret;
        private final int ttlHours;

        public AdminJwtUtil(String secret, int ttlHours) {
            this.secret = secret.getBytes();
            this.ttlHours = ttlHours;
        }

        public String generateToken(Long adminId, String username, String role) {
            try {
                var now = new Date();
                var claims = new JWTClaimsSet.Builder()
                    .subject(String.valueOf(adminId))
                    .claim("username", username)
                    .claim("role", role)
                    .issueTime(now)
                    .expirationTime(new Date(now.getTime() + ttlHours * 3600_000L))
                    .build();
                var signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
                    .type(JOSEObjectType.JWT).build(), claims);
                signed.sign(new MACSigner(this.secret));
                return signed.serialize();
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate JWT", e);
            }
        }

        public JWTClaimsSet parse(String token) {
            try {
                var jwt = SignedJWT.parse(token);
                if (!jwt.verify(new MACVerifier(this.secret))) {
                    return null;
                }
                var claims = jwt.getJWTClaimsSet();
                if (claims.getExpirationTime().before(new Date())) {
                    return null;
                }
                return claims;
            } catch (Exception e) {
                return null;
            }
        }
    }

    static class AdminJwtFilter extends OncePerRequestFilter {
        private final String jwtSecret;

        AdminJwtFilter(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        /**
         * Run on ERROR dispatches too. By default OncePerRequestFilter skips them,
         * which left the SecurityContext empty on the /error forward — so unmapped
         * paths surfaced as 401 (entry point) instead of 404, silently logging the
         * client out. Authenticating the error dispatch lets the real 404 through.
         */
        @Override
        protected boolean shouldNotFilterErrorDispatch() {
            return false;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain)
                throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                var util = new AdminJwtUtil(jwtSecret, 0);
                var claims = util.parse(token);
                if (claims != null) {
                    String role = (String) claims.getClaim("role");
                    var auth = new UsernamePasswordAuthenticationToken(
                        Long.valueOf(claims.getSubject()),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    Object username = claims.getClaim("username");
                    if (username != null) {
                        auth.setDetails(username);
                    }
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}

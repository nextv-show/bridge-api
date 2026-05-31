package com.sanshuiyuan.asset.config;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * 校验 Bearer JWT 的<b>签名</b>与有效期，通过后以 {@code sub}（userId）建立认证。
 *
 * <p>密钥/算法与 user-service 的 {@code JwtIssuer} 一致（HS256 + 同一 {@code jwt.secret}，
 * {@link #padSecret} 补齐逻辑也必须一致），asset-service 才能验签 user-service 签发的 access token。
 *
 * <p><b>安全修复</b>：此前本过滤器只 {@code SignedJWT.parse()} 取 {@code sub} 而<b>不验签</b>，
 * 任何人可伪造 {@code {"sub":"<任意 userId>"}} 冒充他人（越权 / IDOR）。现强制验签 + 校验过期，
 * 任一不通过即不建立认证（请求按未登录处理 → 403）。
 */
public class JwtBearerFilter extends OncePerRequestFilter {

    private final JWSVerifier verifier;

    public JwtBearerFilter(String secret) {
        try {
            this.verifier = new MACVerifier(padSecret(secret));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize JWT verifier", e);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Long userId = verifyAndExtractUserId(header.substring(7));
            if (userId != null) {
                var auth = new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 验签 + 校验过期，成功返回 userId；任何失败（签名不符 / 过期 / 格式非法 / sub 非数字）返回 null。 */
    private Long verifyAndExtractUserId(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(verifier)) {
                return null; // 签名无效：伪造 / 被篡改 / 非本密钥签发
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (exp != null && exp.before(new Date())) {
                return null; // 已过期
            }
            String sub = claims.getSubject();
            return sub == null ? null : Long.valueOf(sub);
        } catch (Exception e) {
            return null; // 解析 / 验签 / 解析数字失败：一律按未认证处理
        }
    }

    /** 与 user-service JwtIssuer.padSecret 完全一致：不足 32 字节右补零，否则原样。 */
    static byte[] padSecret(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= 32) {
            return bytes;
        }
        byte[] padded = new byte[32];
        System.arraycopy(bytes, 0, padded, 0, bytes.length);
        return padded;
    }
}

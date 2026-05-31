package com.sanshuiyuan.asset.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 安全回归：JwtBearerFilter 必须验签。覆盖伪造（不验签时会通过的攻击向量）/异密钥/过期/合法。
 */
class JwtBearerFilterTest {

    // 与 user-service JwtIssuer 默认一致（prod 两端均未设 JWT_SECRET 时回退此值）。
    private static final String SECRET = "change-me-in-production-at-least-256-bits-long!!";

    private final JwtBearerFilter filter = new JwtBearerFilter(SECRET);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static String sign(String secret, String sub, Date exp) throws Exception {
        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder().subject(sub).issueTime(new Date());
        if (exp != null) {
            b.expirationTime(exp);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), b.build());
        jwt.sign(new MACSigner(JwtBearerFilter.padSecret(secret)));
        return jwt.serialize();
    }

    private Authentication runWith(String authHeader) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (authHeader != null) {
            req.addHeader("Authorization", authHeader);
        }
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void validSignedToken_authenticatesWithUserId() throws Exception {
        String token = sign(SECRET, "42", new Date(System.currentTimeMillis() + 60_000));
        Authentication auth = runWith("Bearer " + token);
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(42L);
    }

    @Test
    void forgedUnsignedToken_rejected() throws Exception {
        // {"alg":"HS256"}.{"sub":"42"}.<垃圾签名> —— 即此前漏洞下能冒充任意用户的攻击向量。
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0MiJ9.AAAA";
        assertThat(runWith("Bearer " + forged)).isNull();
    }

    @Test
    void tokenSignedWithWrongSecret_rejected() throws Exception {
        String token = sign("a-totally-different-secret-32bytes-long!!", "42",
                new Date(System.currentTimeMillis() + 60_000));
        assertThat(runWith("Bearer " + token)).isNull();
    }

    @Test
    void expiredToken_rejected() throws Exception {
        String token = sign(SECRET, "42", new Date(System.currentTimeMillis() - 1_000));
        assertThat(runWith("Bearer " + token)).isNull();
    }

    @Test
    void noAuthorizationHeader_unauthenticated() throws Exception {
        assertThat(runWith(null)).isNull();
    }
}

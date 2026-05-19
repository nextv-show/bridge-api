package com.sanshuiyuan.user.infra.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sanshuiyuan.user.domain.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtIssuer {

    private final JWSSigner signer;
    private final JWSVerifier verifier;
    private final long accessTtlMillis;
    private final long refreshTtlMillis;

    public JwtIssuer(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-ttl}") String accessTtl,
            @Value("${jwt.refresh-token-ttl}") String refreshTtl) {
        byte[] keyBytes = padSecret(secret);
        try {
            this.signer = new MACSigner(keyBytes);
            this.verifier = new MACVerifier(keyBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize JWT signer", e);
        }
        this.accessTtlMillis = parseDuration(accessTtl);
        this.refreshTtlMillis = parseDuration(refreshTtl);
    }

    public String issueAccessToken(Long userId, Role activeRole) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(userId))
                .claim("role", activeRole.name())
                .claim("type", "access")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusMillis(accessTtlMillis)))
                .build();
        return signJwt(claims);
    }

    public String issueRefreshToken(Long userId) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusMillis(refreshTtlMillis)))
                .build();
        return signJwt(claims);
    }

    public Map<String, Object> parseToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(verifier)) {
                throw new RuntimeException("Invalid JWT signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() != null && claims.getExpirationTime().before(new Date())) {
                throw new RuntimeException("JWT token expired");
            }
            Map<String, Object> result = new HashMap<>();
            result.put("sub", claims.getSubject());
            result.put("type", claims.getStringClaim("type"));
            String role = claims.getStringClaim("role");
            if (role != null) {
                result.put("role", role);
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JWT token", e);
        }
    }

    private String signJwt(JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }

    private static byte[] padSecret(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= 32) return bytes;
        byte[] padded = new byte[32];
        System.arraycopy(bytes, 0, padded, 0, bytes.length);
        return padded;
    }

    private static long parseDuration(String spec) {
        spec = spec.trim().toLowerCase();
        if (spec.endsWith("d")) {
            return Long.parseLong(spec.replace("d", "")) * 24 * 60 * 60 * 1000L;
        } else if (spec.endsWith("h")) {
            return Long.parseLong(spec.replace("h", "")) * 60 * 60 * 1000L;
        } else if (spec.endsWith("m")) {
            return Long.parseLong(spec.replace("m", "")) * 60 * 1000L;
        }
        return Long.parseLong(spec) * 1000L;
    }
}

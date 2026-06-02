package com.sanshuiyuan.logistics.auth;

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 校验 h5-service 签发的 H5 JWT（同 matching-service H5JwtService，同密钥/算法 HS256）。
 */
public class H5JwtService {
    private final byte[] secret;

    public H5JwtService(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 校验并返回 subject；非法/过期返回 null。 */
    public String parseSubject(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(this.secret))) {
                return null;
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
                return null;
            }
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}

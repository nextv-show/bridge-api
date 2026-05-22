package com.sanshuiyuan.h5.auth;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * H5 端会话 JWT：以微信 openid 为 subject 签发/校验。
 * 与 admin-service 的 JWT 完全独立（不同 secret、不同前缀）。HS256。
 */
public class H5JwtService {

    private final byte[] secret;
    private final long ttlMillis;

    public H5JwtService(String secret, int ttlHours) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlMillis = ttlHours * 3600_000L;
    }

    public String generate(String openid) {
        try {
            Date now = new Date();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(openid)
                    .issueTime(now)
                    .expirationTime(new Date(now.getTime() + ttlMillis))
                    .build();
            SignedJWT signed = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                    claims);
            signed.sign(new MACSigner(this.secret));
            return signed.serialize();
        } catch (Exception e) {
            throw new RuntimeException("签发 H5 JWT 失败", e);
        }
    }

    /** 校验并返回 openid（subject）；非法或过期返回 null。 */
    public String parseOpenid(String token) {
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

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
 * H5 端会话 JWT：以统一身份（unionid，拿不到回退渠道 openid）为 subject 签发/校验。
 * 与 admin-service 的 JWT 完全独立（不同 secret、不同前缀）。HS256。
 *
 * <p>除 subject（= 各表 openid 列的主键）外，额外携带两个 claim：
 * <ul>
 *   <li>{@code pay_openid} —— 渠道 JSAPI 预支付的 payer.openid（公众号会话=公众号 openid，
 *       小程序会话=小程序 openid）。与 subject（统一身份）解耦：subject 可能是 unionid，不能直接当 payer。</li>
 *   <li>{@code ct} —— clientType（{@code H5}/{@code MINI}），用于选支付通道并透传 {@code X-Client-Type}。</li>
 * </ul>
 */
public class H5JwtService {

    /** 渠道支付 openid claim 名。 */
    public static final String CLAIM_PAY_OPENID = "pay_openid";
    public static final String CLAIM_CLIENT_TYPE = "ct";

    private final byte[] secret;
    private final long ttlMillis;

    public H5JwtService(String secret, int ttlHours) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlMillis = ttlHours * 3600_000L;
    }

    /** 兼容签发：subject 同时作为渠道支付 openid（旧公众号语义），clientType=H5。 */
    public String generate(String openid) {
        return generate(openid, openid, "H5");
    }

    /**
     * 统一签发。
     *
     * @param subject    统一身份（unionid 优先，回退渠道 openid）——作为 principal/各表主键。
     * @param payOpenid  渠道 JSAPI 预支付 payer.openid（公众号/小程序 openid）。
     * @param clientType {@code H5} 或 {@code MINI}。
     */
    public String generate(String subject, String payOpenid, String clientType) {
        try {
            Date now = new Date();
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issueTime(now)
                    .expirationTime(new Date(now.getTime() + ttlMillis));
            if (payOpenid != null && !payOpenid.isBlank()) {
                builder.claim(CLAIM_PAY_OPENID, payOpenid);
            }
            if (clientType != null && !clientType.isBlank()) {
                builder.claim(CLAIM_CLIENT_TYPE, clientType);
            }
            SignedJWT signed = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                    builder.build());
            signed.sign(new MACSigner(this.secret));
            return signed.serialize();
        } catch (Exception e) {
            throw new RuntimeException("签发 H5 JWT 失败", e);
        }
    }

    /** 校验并返回 subject（统一身份）；非法或过期返回 null。保留供旧调用方使用。 */
    public String parseOpenid(String token) {
        H5Principal p = parse(token);
        return p == null ? null : p.canonicalId();
    }

    /** 校验并返回完整 principal（含 mp_openid / clientType）；非法或过期返回 null。 */
    public H5Principal parse(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(this.secret))) {
                return null;
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
                return null;
            }
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                return null;
            }
            String payOpenid = claimString(claims, CLAIM_PAY_OPENID);
            String clientType = claimString(claims, CLAIM_CLIENT_TYPE);
            return new H5Principal(subject, payOpenid, clientType == null ? "H5" : clientType);
        } catch (Exception e) {
            return null;
        }
    }

    private static String claimString(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析出的会话主体。
     *
     * @param canonicalId 统一身份（subject）——各表 openid 主键。
     * @param payOpenid   渠道 JSAPI 预支付 payer.openid（公众号/小程序 openid）。
     * @param clientType  {@code H5} 或 {@code MINI}。
     */
    public record H5Principal(String canonicalId, String payOpenid, String clientType) {}
}

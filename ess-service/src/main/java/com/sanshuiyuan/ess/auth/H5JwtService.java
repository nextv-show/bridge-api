package com.sanshuiyuan.ess.auth;

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
 * H5 端会话 JWT 校验器（与 cend-service 同源、同密钥 {@code H5_JWT_SECRET}、HS256）。
 * <p>
 * ess-service 只需要 <b>校验</b> H5 token 并取出 subject（统一身份 / 各表 openid 主键）。
 * 不签发 token（签发在 cend-service）；保留 {@link #generate} 仅供单测构造。
 *
 * <p>除 subject 外携带 {@code pay_openid}（渠道支付 openid）与 {@code ct}（clientType），
 * 与 cend-service 的 {@code H5JwtService} 保持 claim 兼容。
 */
public class H5JwtService {

    public static final String CLAIM_PAY_OPENID = "pay_openid";
    public static final String CLAIM_CLIENT_TYPE = "ct";

    private final byte[] secret;
    private final long ttlMillis;

    public H5JwtService(String secret, int ttlHours) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlMillis = ttlHours * 3600_000L;
    }

    /** 兼容签发：subject 同时作为渠道支付 openid，clientType=H5。主要供单测使用。 */
    public String generate(String openid) {
        return generate(openid, openid, "H5");
    }

    /**
     * 签发（与 cend-service 同 claim 结构）。主要供单测构造合法 token。
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

    /** 校验并返回 subject（统一身份）；非法或过期返回 null。 */
    public String parseOpenid(String token) {
        H5Principal p = parse(token);
        return p == null ? null : p.canonicalId();
    }

    /** 校验并返回完整 principal；非法或过期返回 null。 */
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

    /** 解析出的会话主体（与 cend-service 同结构）。 */
    public record H5Principal(String canonicalId, String payOpenid, String clientType) {}
}

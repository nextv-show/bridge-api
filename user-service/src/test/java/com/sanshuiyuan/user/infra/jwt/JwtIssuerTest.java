package com.sanshuiyuan.user.infra.jwt;

import com.sanshuiyuan.user.domain.Role;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F.1: JWT 签发/校验回归 —— access 含 role、refresh 不含 role、type 区分、
 * 过期与签名篡改被拒。TTL 通过构造参数注入（与 application.yml 的 2h/14d 同解析逻辑）。
 */
class JwtIssuerTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long!!";

    private JwtIssuer issuer(String accessTtl, String refreshTtl) {
        return new JwtIssuer(SECRET, accessTtl, refreshTtl);
    }

    @Test
    void accessToken_carriesRoleAndAccessType() {
        JwtIssuer jwt = issuer("2h", "14d");
        Map<String, Object> claims = jwt.parseToken(jwt.issueAccessToken(42L, Role.OWNER));

        assertThat(claims.get("sub")).isEqualTo("42");
        assertThat(claims.get("type")).isEqualTo("access");
        assertThat(claims.get("role")).isEqualTo("OWNER");
    }

    @Test
    void refreshToken_isRefreshTypeWithoutRole() {
        JwtIssuer jwt = issuer("2h", "14d");
        Map<String, Object> claims = jwt.parseToken(jwt.issueRefreshToken(42L));

        assertThat(claims.get("sub")).isEqualTo("42");
        assertThat(claims.get("type")).isEqualTo("refresh");
        assertThat(claims.get("role")).isNull();
    }

    @Test
    void expiredToken_isRejected() {
        // 0 TTL → 立即过期
        JwtIssuer jwt = issuer("0", "0");
        String token = jwt.issueAccessToken(42L, Role.CONSUMER);

        assertThatThrownBy(() -> jwt.parseToken(token))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tokenSignedWithDifferentSecret_failsSignatureVerification() {
        String token = issuer("2h", "14d").issueAccessToken(42L, Role.CONSUMER);
        JwtIssuer other = new JwtIssuer("another-secret-key-also-32-bytes-long!!!", "2h", "14d");

        assertThatThrownBy(() -> other.parseToken(token))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void malformedToken_isRejected() {
        JwtIssuer jwt = issuer("2h", "14d");
        assertThatThrownBy(() -> jwt.parseToken("not-a-jwt"))
                .isInstanceOf(RuntimeException.class);
    }
}

package com.sanshuiyuan.user.referral;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T8a.5：RefIdCodec 单元测试 —— encode→decode 还原、伪造/篡改 token 拒绝、密钥驱动（无硬编码密钥）。
 */
class RefIdCodecTest {

    private static final String SECRET = "unit-test-ref-id-secret-0123456789";
    private final RefIdCodec codec = new RefIdCodec(SECRET);

    @Test
    void encodeThenDecode_restoresOriginalUserId() {
        for (long userId : new long[]{1L, 42L, 14821L, Long.MAX_VALUE}) {
            String token = codec.encode(userId);
            assertThat(codec.decode(token)).isEqualTo(userId);
        }
    }

    @Test
    void encode_isDeterministicAndUrlSafe() {
        String token = codec.encode(14821L);
        // 确定性：同 userId 同密钥两次编码一致。
        assertThat(codec.encode(14821L)).isEqualTo(token);
        // Base64URL：无 '+' '/' '=' 填充，且为 payload.sig 两段。
        assertThat(token).doesNotContain("+").doesNotContain("/").doesNotContain("=");
        assertThat(token.split("\\.")).hasSize(2);
    }

    @Test
    void decode_tamperedSignature_isRejected() {
        String token = codec.encode(14821L);
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");
        assertThatThrownBy(() -> codec.decode(tampered))
                .isInstanceOf(InvalidRefIdException.class);
    }

    @Test
    void decode_tamperedPayload_isRejected() {
        // 自造 payload=999（指向他人 id）但签名仍是 14821 的 → 应被签名校验拒绝。
        String token = codec.encode(14821L);
        String forgedPayloadPart = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("999".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String forged = forgedPayloadPart + "." + token.split("\\.")[1];
        assertThatThrownBy(() -> codec.decode(forged))
                .isInstanceOf(InvalidRefIdException.class);
    }

    @Test
    void decode_tokenFromDifferentSecret_isRejected() {
        // 密钥驱动：另一密钥签发的 token 在本密钥下无法通过校验，证明签名依赖注入密钥而非硬编码。
        RefIdCodec other = new RefIdCodec("a-completely-different-secret-key-9876");
        String tokenFromOther = other.encode(14821L);
        assertThat(other.decode(tokenFromOther)).isEqualTo(14821L);
        assertThatThrownBy(() -> codec.decode(tokenFromOther))
                .isInstanceOf(InvalidRefIdException.class);
        // 同一 userId、不同密钥 → token 不同（签名取决于密钥）。
        assertThat(tokenFromOther).isNotEqualTo(codec.encode(14821L));
    }

    @Test
    void decode_malformedTokens_areRejected() {
        for (String bad : new String[]{null, "", "   ", "no-dot", "a.b.c", "!!!.???"}) {
            assertThatThrownBy(() -> codec.decode(bad))
                    .as("token=%s", bad)
                    .isInstanceOf(InvalidRefIdException.class);
        }
    }

    @Test
    void construct_withBlankSecret_isRejected() {
        assertThatThrownBy(() -> new RefIdCodec(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefIdCodec(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

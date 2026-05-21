package com.sanshuiyuan.h5.checkout.infra.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdCardCipherTest {

    private IdCardCipher cipher;

    @BeforeEach
    void setUp() {
        // 256-bit AES key (32 bytes)
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        KeyProvider keyProvider = () -> key;
        cipher = new IdCardCipher(keyProvider);
    }

    @Test
    void encryptDecrypt_roundTrip_restoresOriginalPlaintext() {
        String plaintext = "110101199003071234";
        byte[] ciphertext = cipher.encrypt(plaintext);
        String decrypted = cipher.decrypt(ciphertext);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encrypt_differentIvEachTime_ciphertextDiffers() {
        String plaintext = "110101199003071234";
        byte[] cipher1 = cipher.encrypt(plaintext);
        byte[] cipher2 = cipher.encrypt(plaintext);
        assertThat(cipher1).isNotEqualTo(cipher2);
    }

    @Test
    void decrypt_tamperedCiphertext_throwsException() {
        String plaintext = "110101199003071234";
        byte[] ciphertext = cipher.encrypt(plaintext);
        // Tamper: flip a byte in the ciphertext area (after IV)
        ciphertext[ciphertext.length - 1] ^= 0xFF;
        assertThatThrownBy(() -> cipher.decrypt(ciphertext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AES-GCM decrypt failed");
    }

    @Test
    void maskingUtils_idCardMask_correctFormat() {
        String idCard = "110101199003071234";
        String masked = MaskingUtils.maskIdCard(idCard);
        // Should show first 3 and last 2
        assertThat(masked).startsWith("110");
        assertThat(masked).endsWith("34");
        assertThat(masked).contains("*************");
        assertThat(masked).doesNotContain("199003");
    }

    @Test
    void maskingUtils_realNameMask_correctFormat() {
        String name = "张三";
        String masked = MaskingUtils.maskRealName(name);
        assertThat(masked).isEqualTo("张 **");
    }

    @Test
    void maskingUtils_idCardMask_shortInput_returnsAsterisks() {
        assertThat(MaskingUtils.maskIdCard(null)).isEqualTo("***");
        assertThat(MaskingUtils.maskIdCard("123")).isEqualTo("***");
    }

    @Test
    void maskingUtils_realNameMask_emptyInput_returnsAsterisks() {
        assertThat(MaskingUtils.maskRealName(null)).isEqualTo("***");
        assertThat(MaskingUtils.maskRealName("")).isEqualTo("***");
    }
}

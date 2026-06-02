package com.sanshuiyuan.matching.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class IdCardCipherTest {

    private IdCardCipher cipher() {
        // 默认 dev 主密钥（32 字节全 0 的 Base64）。
        KeyProvider kp = new EnvKeyProvider("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        return new IdCardCipher(kp);
    }

    @Test
    void encryptDecrypt_roundTrip() {
        IdCardCipher c = cipher();
        String plain = "13812345678";
        byte[] enc = c.encrypt(plain);
        assertEquals(plain, c.decrypt(enc));
    }

    @Test
    void encrypt_randomIv_differentCiphertext() {
        IdCardCipher c = cipher();
        byte[] a = c.encrypt("13812345678");
        byte[] b = c.encrypt("13812345678");
        // 随机 IV → 同明文密文不同，但都能解回。
        assertFalse(java.util.Arrays.equals(a, b));
        assertEquals(c.decrypt(a), c.decrypt(b));
    }

    @Test
    void idCardHash_deterministic() {
        IdCardCipher c = cipher();
        String h1 = c.idCardHash("13812345678");
        String h2 = c.idCardHash("13812345678");
        assertEquals(h1, h2);
        assertEquals(64, h1.length());   // hex of 32-byte SHA256
    }

    @Test
    void idCardHash_differentInputs_differentHash() {
        IdCardCipher c = cipher();
        assertNotEquals(c.idCardHash("13812345678"), c.idCardHash("13800000000"));
    }

    @Test
    void hash_stableAcrossInstances() {
        assertArrayEquals(
                cipher().idCardHash("13812345678").getBytes(),
                cipher().idCardHash("13812345678").getBytes());
    }
}

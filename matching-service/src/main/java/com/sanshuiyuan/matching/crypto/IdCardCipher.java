package com.sanshuiyuan.matching.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * AES-GCM 加解密 + HMAC-SHA256 确定性哈希。自 h5-service checkout/infra/crypto 拷贝（改包名）。
 * 撮合服务用于：手机号 encrypt→contact_phone_enc、idCardHash(规范化手机号)→contact_phone_hash。
 */
@Component
public class IdCardCipher {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecretKeySpec hmacKeySpec;
    private final SecureRandom random = new SecureRandom();

    public IdCardCipher(KeyProvider keyProvider) {
        byte[] key = keyProvider.getKey();
        this.keySpec = new SecretKeySpec(key, "AES");
        this.hmacKeySpec = new SecretKeySpec(key, HMAC_ALGO);
    }

    /**
     * 确定性哈希（HMAC-SHA256，密钥同 AES）。AES-GCM 随机 IV 无法等值比对，
     * 故用该哈希作为「同手机号」查询/限频键。入参应为已规范化（去空格/连字符）的手机号。
     */
    public String idCardHash(String normalized) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(hmacKeySpec);
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC hash failed", e);
        }
    }

    public byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
            byte[] out = new byte[IV_LEN + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ciphertext, 0, out, IV_LEN, ciphertext.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encrypt failed", e);
        }
    }

    public String decrypt(byte[] ivAndCiphertext) {
        try {
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(ivAndCiphertext, 0, iv, 0, IV_LEN);
            byte[] ciphertext = new byte[ivAndCiphertext.length - IV_LEN];
            System.arraycopy(ivAndCiphertext, IV_LEN, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decrypt failed", e);
        }
    }
}

package com.sanshuiyuan.matching.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

/** 从配置读取 Base64 AES 主密钥（与 h5-service 同值）。 */
@Component
public class EnvKeyProvider implements KeyProvider {

    private final byte[] key;

    public EnvKeyProvider(@Value("${h5.aes-master-key:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=}") String base64Key) {
        this.key = Base64.getDecoder().decode(base64Key);
    }

    @Override
    public byte[] getKey() {
        return key;
    }
}

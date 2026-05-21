package com.sanshuiyuan.h5.checkout.infra.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

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

package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.crypto.IdCardCipher;
import com.sanshuiyuan.matching.request.api.dto.RequestItem;
import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import com.sanshuiyuan.matching.request.domain.PriceTier;
import com.sanshuiyuan.matching.request.domain.RequestStatus;
import com.sanshuiyuan.matching.request.domain.SceneType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * 守护：单条记录解密失败（如密钥轮换后旧密文）不得让整张"我的需求"列表 500。
 * {@link RequestItemMapper#toItem} 捕获解密异常并回填占位符，不向上抛。
 */
@ExtendWith(MockitoExtension.class)
class RequestItemMapperTest {

    @Mock
    IdCardCipher cipher;

    /** 构造一条最小可映射的需求单（仅 toItem 实际读取的字段）。 */
    private static MatchingRequest sampleRequest() {
        MatchingRequest r = new MatchingRequest();
        r.setId(42L);
        r.setUserId(7L);
        r.setContactName("张三");
        r.setContactPhoneEnc(new byte[]{1, 2, 3});
        r.setAddress("某地");
        r.setLat(new BigDecimal("30.1234567"));
        r.setLng(new BigDecimal("120.7654321"));
        r.setSceneType(SceneType.HOME);
        r.setEstDailyLiters(50);
        r.setExpectedPriceTier(PriceTier.T_080);
        r.setStatus(RequestStatus.OPEN);
        return r;
    }

    @Test
    void decryptSuccess_plainPhone_returnsPlaintext() {
        RequestItemMapper mapper = new RequestItemMapper(cipher);
        MatchingRequest r = sampleRequest();
        when(cipher.decrypt(r.getContactPhoneEnc())).thenReturn("13800005678");

        RequestItem item = mapper.toItem(r, true, null);

        assertThat(item.contactPhone()).isEqualTo("13800005678");
    }

    @Test
    void decryptSuccess_maskedPhone_returnsMasked() {
        RequestItemMapper mapper = new RequestItemMapper(cipher);
        MatchingRequest r = sampleRequest();
        when(cipher.decrypt(r.getContactPhoneEnc())).thenReturn("13800005678");

        RequestItem item = mapper.toItem(r, false, null);

        assertThat(item.contactPhone()).isEqualTo("138****5678");
    }

    @Test
    void decryptThrows_returnsPlaceholder_andDoesNotPropagate() {
        RequestItemMapper mapper = new RequestItemMapper(cipher);
        MatchingRequest r = sampleRequest();
        when(cipher.decrypt(r.getContactPhoneEnc()))
                .thenThrow(new IllegalStateException("AES-GCM decrypt failed"));

        assertThatCode(() -> {
            RequestItem item = mapper.toItem(r, false, null);
            assertThat(item.contactPhone()).isEqualTo("***（解密异常）");
        }).doesNotThrowAnyException();
    }

    @Test
    void decryptThrows_plainPhoneMode_alsoReturnsPlaceholder() {
        RequestItemMapper mapper = new RequestItemMapper(cipher);
        MatchingRequest r = sampleRequest();
        when(cipher.decrypt(r.getContactPhoneEnc()))
                .thenThrow(new IllegalStateException("AES-GCM decrypt failed"));

        RequestItem item = mapper.toItem(r, true, null);

        assertThat(item.contactPhone()).isEqualTo("***（解密异常）");
    }
}

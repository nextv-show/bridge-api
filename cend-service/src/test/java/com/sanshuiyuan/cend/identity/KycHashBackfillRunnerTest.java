package com.sanshuiyuan.cend.identity;

import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import com.sanshuiyuan.cend.checkout.infra.crypto.IdCardCipher;
import com.sanshuiyuan.cend.checkout.infra.repository.KycRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 启动回填守卫：存量 PASS 记录补齐 phone_hash 与 id_card_hash（V022/V025 之前缺失）；
 * 整批无进展即停，避免脏数据空转。
 */
@ExtendWith(MockitoExtension.class)
class KycHashBackfillRunnerTest {

    @Mock KycRecordRepository kycRepo;
    @Mock IdCardCipher cipher;

    private KycRecord rec(byte[] phoneEnc, byte[] idCardNoEnc) {
        try {
            var ctor = KycRecord.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            KycRecord r = ctor.newInstance();
            set(r, "status", KycStatus.PASS);
            if (phoneEnc != null) set(r, "phoneEnc", phoneEnc);
            if (idCardNoEnc != null) set(r, "idCardNoEnc", idCardNoEnc);
            return r;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void set(Object t, String f, Object v) {
        try {
            var fl = t.getClass().getDeclaredField(f);
            fl.setAccessible(true);
            fl.set(t, v);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void backfillsBothPhoneAndIdCardHash() {
        KycRecord r1 = rec(new byte[]{1}, null);  // 缺 phone_hash
        KycRecord r2 = rec(null, new byte[]{2});  // 缺 id_card_hash
        when(kycRepo.findByStatusAndPhoneHashIsNullAndPhoneEncIsNotNull(eq(KycStatus.PASS), any()))
                .thenReturn(List.of(r1)).thenReturn(List.of());
        when(kycRepo.findByStatusAndIdCardHashIsNullAndIdCardNoEncIsNotNull(eq(KycStatus.PASS), any()))
                .thenReturn(List.of(r2)).thenReturn(List.of());
        when(cipher.decrypt(any())).thenAnswer(inv -> {
            byte[] b = inv.getArgument(0);
            return b[0] == 1 ? "13800138000" : "110101199003077432";
        });
        when(cipher.phoneHash("13800138000")).thenReturn("ph");
        when(cipher.idCardHash("110101199003077432")).thenReturn("ih");

        new KycHashBackfillRunner(kycRepo, cipher).backfillAll();

        assertThat(r1.getPhoneHash()).isEqualTo("ph");
        assertThat(r2.getIdCardHash()).isEqualTo("ih");
        verify(kycRepo).save(r1);
        verify(kycRepo).save(r2);
    }

    @Test
    void stopsOnStuckBatch_noProgress() {
        KycRecord bad = rec(new byte[]{9}, null);
        when(kycRepo.findByStatusAndPhoneHashIsNullAndPhoneEncIsNotNull(eq(KycStatus.PASS), any()))
                .thenReturn(List.of(bad)); // 永远返回同一条
        when(cipher.decrypt(any())).thenReturn(null); // 解密失败 → filler 返回 false → 整批无进展

        int n = new KycHashBackfillRunner(kycRepo, cipher).backfill(
                "phone_hash",
                page -> kycRepo.findByStatusAndPhoneHashIsNullAndPhoneEncIsNotNull(KycStatus.PASS, page),
                r -> {
                    String p = cipher.decrypt(r.getPhoneEnc());
                    if (p == null) return false;
                    r.bindPhoneHash(cipher.phoneHash(p.trim()));
                    return true;
                });

        assertThat(n).isZero();
        verify(kycRepo, never()).save(any());
    }
}

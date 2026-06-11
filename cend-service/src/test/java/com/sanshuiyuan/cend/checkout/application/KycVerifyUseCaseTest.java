package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.api.dto.KycVerifyResponse;
import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import com.sanshuiyuan.cend.checkout.infra.aliyun.AliyunKycClient;
import com.sanshuiyuan.cend.checkout.infra.repository.KycRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 一证一号"同证同人则链接"行为守卫（H5 活体路径）：当同一身份证已在其他 openid 下 PASS 时，
 * 第二端活体通过后不再抛 KYC_ID_CARD_CONFLICT，而是正常把本端记录置 PASS（两端经 id_card_hash 链接）。
 */
@ExtendWith(MockitoExtension.class)
class KycVerifyUseCaseTest {

    @Mock KycRecordRepository kycRepo;
    @Mock AliyunKycClient kycClient;

    private KycRecord initRecord(String openid, String idCardHash) {
        try {
            var ctor = KycRecord.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            KycRecord r = ctor.newInstance();
            set(r, "openid", openid);
            set(r, "idCardHash", idCardHash);
            set(r, "status", KycStatus.INIT);
            set(r, "realNameMask", "张 **");
            set(r, "idCardNoMask", "110*************34");
            set(r, "phoneMask", "138****8000");
            return r;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void set(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void sameIdOnSecondOpenid_linksAndPromotesToPass_insteadOfRejecting() {
        KycRecord init = initRecord("mini-openid", "hash-x");
        when(kycClient.queryResult("cert-1"))
                .thenReturn(new AliyunKycClient.KycVerifyResult(true, "张三", "110101199003077432"));
        when(kycRepo.findFirstByCertifyIdAndOpenidAndStatus("cert-1", "mini-openid", KycStatus.INIT))
                .thenReturn(Optional.of(init));
        // 同证已在其他 openid（公众号）下 PASS。
        when(kycRepo.existsByIdCardHashAndStatusAndOpenidNot("hash-x", KycStatus.PASS, "mini-openid"))
                .thenReturn(true);
        when(kycRepo.findAllByOpenidAndStatus("mini-openid", KycStatus.PASS))
                .thenReturn(List.of());

        KycVerifyResponse resp = new KycVerifyUseCase(kycRepo, kycClient).execute("cert-1", "mini-openid");

        // 不抛冲突；本端置 PASS，建立同人跨端链接。
        assertThat(resp.status()).isEqualTo("PASS");
        assertThat(init.getStatus()).isEqualTo(KycStatus.PASS);
        verify(kycRepo).save(init);
    }
}

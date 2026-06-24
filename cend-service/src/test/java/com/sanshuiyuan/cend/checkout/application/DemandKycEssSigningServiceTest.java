package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import com.sanshuiyuan.cend.checkout.infra.crypto.IdCardCipher;
import com.sanshuiyuan.cend.checkout.infra.repository.KycRecordRepository;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.infra.client.EssServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 实名认证 / 用水需求发布承诺电子签编排守卫（spec 107）。
 */
@ExtendWith(MockitoExtension.class)
class DemandKycEssSigningServiceTest {

    private static final String OPENID = "oABC123";
    private static final String VALID_ID = "110101199003077432";
    private static final String VALID_PHONE = "13800138000";

    @Mock private KycRecordRepository kycRepo;
    @Mock private IdCardCipher cipher;
    @Mock private EssServiceClient essClient;
    @Mock private SubscribeSigningService subscribeSigningService;

    private DemandKycEssSigningService service() {
        return new DemandKycEssSigningService(kycRepo, cipher, essClient, subscribeSigningService);
    }

    private void stubCipher() {
        lenient().when(cipher.idCardHash(anyString())).thenReturn("idhash");
        lenient().when(cipher.phoneHash(anyString())).thenReturn("phonehash");
        lenient().when(cipher.encrypt(anyString())).thenReturn(new byte[]{1});
    }

    @Test
    void start_whenAlreadyPassed_returnsAlreadyPassedAndSkipsEss() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc(OPENID, KycStatus.PASS))
                .thenReturn(Optional.of(KycRecord.create(OPENID, new byte[]{1}, new byte[]{1},
                        "张*", "110***34", "h", "ESS-KYC-1", "ESS_KYC_AUTH", new byte[]{1}, "138****0000")));

        var r = service().start(OPENID, "Bearer t", 1L, "张三", VALID_ID, VALID_PHONE);

        assertThat(r.alreadyPassed()).isTrue();
        assertThat(r.contractId()).isNull();
        verifyNoInteractions(essClient);
        verify(kycRepo, never()).save(any());
    }

    @Test
    void start_whenNotPassed_generatesKycAuthContractAndSavesInit() {
        stubCipher();
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc(OPENID, KycStatus.PASS))
                .thenReturn(Optional.empty());
        when(kycRepo.existsByIdCardHashAndStatusAndOpenidNot(anyString(), eq(KycStatus.PASS), eq(OPENID)))
                .thenReturn(false);
        when(kycRepo.findFirstByCertifyIdAndOpenidAndStatus("ESS-KYC-77", OPENID, KycStatus.INIT))
                .thenReturn(Optional.empty());
        when(essClient.generateKycAuth(eq("Bearer t"), eq(1L), eq("张三"), eq(VALID_ID), eq(VALID_PHONE)))
                .thenReturn(new EssServiceClient.GenerateResult(77L, "CT-KYC-77", "GENERATED"));

        var r = service().start(OPENID, "Bearer t", 1L, "张三", VALID_ID, VALID_PHONE);

        assertThat(r.alreadyPassed()).isFalse();
        assertThat(r.contractId()).isEqualTo(77L);
        assertThat(r.contractNo()).isEqualTo("CT-KYC-77");
        assertThat(r.phoneMask()).isEqualTo("138****8000");

        // 发起短信短链签署
        verify(essClient).initiateSigning("Bearer t", 77L, 1L, VALID_PHONE, "张三", VALID_ID);

        // 落 INIT：channel=ESS_KYC_AUTH，certifyId=ESS-KYC-77
        ArgumentCaptor<KycRecord> captor = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRepo).save(captor.capture());
        KycRecord init = captor.getValue();
        assertThat(init.getStatus()).isEqualTo(KycStatus.INIT);
        assertThat(init.getChannel()).isEqualTo("ESS_KYC_AUTH");
        assertThat(init.getCertifyId()).isEqualTo("ESS-KYC-77");
    }

    @Test
    void start_withInvalidPhone_throwsValidation() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc(OPENID, KycStatus.PASS))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().start(OPENID, null, null, "张三", VALID_ID, "123"))
                .isInstanceOf(BizException.class);
        verifyNoInteractions(essClient);
    }

    @Test
    void status_whenArchived_promotesInitToPass() {
        when(essClient.status("Bearer t", 77L)).thenReturn("ARCHIVED");
        KycRecord init = KycRecord.createInit(OPENID, new byte[]{1}, new byte[]{1},
                "张*", "110***34", "idhash", "ESS-KYC-77", "ESS_KYC_AUTH", new byte[]{1}, "138****0000");
        when(kycRepo.findFirstByCertifyIdAndOpenidAndStatus("ESS-KYC-77", OPENID, KycStatus.INIT))
                .thenReturn(Optional.of(init));
        when(kycRepo.existsByIdCardHashAndStatusAndOpenidNot("idhash", KycStatus.PASS, OPENID))
                .thenReturn(false);
        when(kycRepo.findAllByOpenidAndStatus(OPENID, KycStatus.PASS)).thenReturn(List.of());

        String status = service().status(OPENID, "Bearer t", 77L);

        assertThat(status).isEqualTo("SIGNED");
        assertThat(init.getStatus()).isEqualTo(KycStatus.PASS);
        verify(kycRepo).save(init);
    }

    @Test
    void status_whenStillSigning_returnsRawAndDoesNotPromote() {
        when(essClient.status("Bearer t", 77L)).thenReturn("SIGNING");

        String status = service().status(OPENID, "Bearer t", 77L);

        assertThat(status).isEqualTo("SIGNING");
        verify(kycRepo, never()).save(any());
    }
}

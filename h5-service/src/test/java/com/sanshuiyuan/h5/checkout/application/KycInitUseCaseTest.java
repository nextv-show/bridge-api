package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.KycInitResponse;
import com.sanshuiyuan.h5.checkout.domain.KycRecord;
import com.sanshuiyuan.h5.checkout.domain.KycStatus;
import com.sanshuiyuan.h5.checkout.infra.aliyun.AliyunKycClient;
import com.sanshuiyuan.h5.checkout.infra.crypto.IdCardCipher;
import com.sanshuiyuan.h5.checkout.infra.repository.KycRecordRepository;
import com.sanshuiyuan.h5.common.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycInitUseCaseTest {

    // 一个校验位正确的测试身份证号。
    private static final String VALID_ID = "110101199003077432";

    @Mock KycRecordRepository kycRepo;
    @Mock AliyunKycClient kycClient;
    @Mock IdCardCipher cipher;

    private KycInitUseCase createUseCase() {
        return new KycInitUseCase(kycRepo, kycClient, cipher, "https://h5.example.com");
    }

    private KycRecord verifiedRecord() {
        try {
            var ctor = KycRecord.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            KycRecord r = ctor.newInstance();
            setField(r, "openid", "openid1");
            setField(r, "status", KycStatus.PASS);
            setField(r, "realNameMask", "张 **");
            setField(r, "idCardNoMask", "110*************34");
            return r;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void execute_alreadyVerified_returnsMaskedInfo() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("openid1", KycStatus.PASS))
                .thenReturn(Optional.of(verifiedRecord()));

        KycInitUseCase uc = createUseCase();
        KycInitResponse resp = uc.execute("openid1", "meta-info-stub", "张三", VALID_ID);

        assertThat(resp.alreadyVerified()).isTrue();
        assertThat(resp.realNameMask()).isEqualTo("张 **");
        assertThat(resp.idCardMask()).isEqualTo("110*************34");
        assertThat(resp.certifyId()).isNull();
        assertThat(resp.verifyToken()).isNull();

        // Should NOT call kycClient
        verifyNoInteractions(kycClient);
    }

    @Test
    void execute_notVerified_returnsInitTokenAndPersistsInitRecord() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("openid1", KycStatus.PASS))
                .thenReturn(Optional.empty());
        when(kycClient.init("openid1", "meta-info-stub", "https://h5.example.com/#/checkout")).thenReturn(
                new AliyunKycClient.KycInitResult("cert-123", "token-abc", "https://verify.example.com")
        );

        KycInitUseCase uc = createUseCase();
        KycInitResponse resp = uc.execute("openid1", "meta-info-stub", "张三", VALID_ID);

        assertThat(resp.alreadyVerified()).isFalse();
        assertThat(resp.certifyId()).isEqualTo("cert-123");
        assertThat(resp.verifyToken()).isEqualTo("token-abc");
        assertThat(resp.verifyUrl()).isEqualTo("https://verify.example.com");

        verify(kycClient).init("openid1", "meta-info-stub", "https://h5.example.com/#/checkout");
        verify(kycRepo).save(org.mockito.ArgumentMatchers.any(KycRecord.class));
    }

    @Test
    void execute_invalidIdCard_rejected() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("openid1", KycStatus.PASS))
                .thenReturn(Optional.empty());

        KycInitUseCase uc = createUseCase();
        assertThatThrownBy(() -> uc.execute("openid1", "meta", "张三", "12345"))
                .isInstanceOf(BizException.class);
        verifyNoInteractions(kycClient);
    }
}

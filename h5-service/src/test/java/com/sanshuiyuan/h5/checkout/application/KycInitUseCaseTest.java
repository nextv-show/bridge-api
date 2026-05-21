package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.KycInitResponse;
import com.sanshuiyuan.h5.checkout.domain.KycRecord;
import com.sanshuiyuan.h5.checkout.domain.KycStatus;
import com.sanshuiyuan.h5.checkout.infra.aliyun.AliyunKycClient;
import com.sanshuiyuan.h5.checkout.infra.repository.KycRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycInitUseCaseTest {

    @Mock KycRecordRepository kycRepo;
    @Mock AliyunKycClient kycClient;

    private KycInitUseCase createUseCase() {
        return new KycInitUseCase(kycRepo, kycClient);
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
        KycInitResponse resp = uc.execute("openid1");

        assertThat(resp.alreadyVerified()).isTrue();
        assertThat(resp.realNameMask()).isEqualTo("张 **");
        assertThat(resp.idCardMask()).isEqualTo("110*************34");
        assertThat(resp.certifyId()).isNull();
        assertThat(resp.verifyToken()).isNull();

        // Should NOT call kycClient
        verifyNoInteractions(kycClient);
    }

    @Test
    void execute_notVerified_returnsInitToken() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("openid1", KycStatus.PASS))
                .thenReturn(Optional.empty());
        when(kycClient.init("openid1")).thenReturn(
                new AliyunKycClient.KycInitResult("cert-123", "token-abc", "https://verify.example.com")
        );

        KycInitUseCase uc = createUseCase();
        KycInitResponse resp = uc.execute("openid1");

        assertThat(resp.alreadyVerified()).isFalse();
        assertThat(resp.certifyId()).isEqualTo("cert-123");
        assertThat(resp.verifyToken()).isEqualTo("token-abc");
        assertThat(resp.verifyUrl()).isEqualTo("https://verify.example.com");
        assertThat(resp.realNameMask()).isNull();
        assertThat(resp.idCardMask()).isNull();

        verify(kycClient).init("openid1");
    }
}

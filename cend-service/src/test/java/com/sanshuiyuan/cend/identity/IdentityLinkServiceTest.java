package com.sanshuiyuan.cend.identity;

import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import com.sanshuiyuan.cend.checkout.infra.crypto.IdCardCipher;
import com.sanshuiyuan.cend.checkout.infra.repository.KycRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 微信手机号核验跨端关联：仅在"微信已验证手机号匹配到已实名(PASS)同人"时建立仅可见关联；
 * 微信未返手机号或无匹配实名时不关联。
 */
@ExtendWith(MockitoExtension.class)
class IdentityLinkServiceTest {

    @Mock WxMiniPhoneClient phoneClient;
    @Mock IdCardCipher cipher;
    @Mock KycRecordRepository kycRepo;
    @Mock IdentityLinkRepository linkRepo;

    private IdentityLinkService service() {
        return new IdentityLinkService(phoneClient, cipher, kycRepo, linkRepo);
    }

    private KycRecord passWithHash(String openid, String idCardHash) {
        try {
            var ctor = KycRecord.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            KycRecord r = ctor.newInstance();
            set(r, "openid", openid);
            set(r, "idCardHash", idCardHash);
            set(r, "status", KycStatus.PASS);
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
    void wechatReturnsNoPhone_notLinked() {
        when(phoneClient.getPhoneNumber("code")).thenReturn(null);

        IdentityLinkService.LinkResult r = service().link("mini-openid", "code");

        assertThat(r.linked()).isFalse();
        verify(linkRepo, never()).save(any());
    }

    @Test
    void noMatchingRealName_notLinked() {
        when(phoneClient.getPhoneNumber("code")).thenReturn("13800138000");
        when(cipher.phoneHash("13800138000")).thenReturn("phash");
        when(kycRepo.findFirstByPhoneHashAndStatusOrderByVerifiedAtDesc("phash", KycStatus.PASS))
                .thenReturn(Optional.empty());

        IdentityLinkService.LinkResult r = service().link("mini-openid", "code");

        assertThat(r.linked()).isFalse();
        verify(linkRepo, never()).save(any());
    }

    @Test
    void phoneMatchesRealName_createsVisibilityLink() {
        when(phoneClient.getPhoneNumber("code")).thenReturn("13800138000");
        when(cipher.phoneHash("13800138000")).thenReturn("phash");
        when(kycRepo.findFirstByPhoneHashAndStatusOrderByVerifiedAtDesc("phash", KycStatus.PASS))
                .thenReturn(Optional.of(passWithHash("wx-openid", "hash-x")));
        // 本端无自有 PASS。
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("mini-openid", KycStatus.PASS))
                .thenReturn(Optional.empty());
        when(linkRepo.findByOpenid("mini-openid")).thenReturn(Optional.empty());

        IdentityLinkService.LinkResult r = service().link("mini-openid", "code");

        assertThat(r.linked()).isTrue();
        verify(linkRepo).save(any(IdentityLink.class));
    }
}

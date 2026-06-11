package com.sanshuiyuan.cend.identity;

import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import com.sanshuiyuan.cend.checkout.infra.repository.KycRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 跨端身份归并守卫：验证"同证同人则链接"后，IdentityResolver 仅按 PASS 的 id_card_hash 把多端 openid
 * 归并为一个自然人（含手机号核验关联 identity_links），且未实名时安全降级为只看本端，绝不据未核验记录聚合（防 IDOR）。
 */
@ExtendWith(MockitoExtension.class)
class IdentityResolverTest {

    @Mock KycRecordRepository kycRepo;
    @Mock IdentityLinkRepository linkRepo;

    private KycRecord rec(String openid, String idCardHash) {
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
    void aggregatesAllOpenidsSharingVerifiedIdCardHash() {
        // 小程序 openid 已 PASS（hash-x）；同一 hash 下另有公众号 openid 的 PASS 记录。
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("mini-openid", KycStatus.PASS))
                .thenReturn(Optional.of(rec("mini-openid", "hash-x")));
        when(kycRepo.findAllByIdCardHashAndStatus("hash-x", KycStatus.PASS))
                .thenReturn(List.of(rec("wx-openid", "hash-x"), rec("mini-openid", "hash-x")));

        Set<String> owned = new IdentityResolver(kycRepo, linkRepo).resolveOwnedOpenids("mini-openid");

        // 两端 openid 归并为同一自然人，去重。
        assertThat(owned).containsExactlyInAnyOrder("mini-openid", "wx-openid");
    }

    @Test
    void aggregatesViaPhoneLinkWhenNoOwnKyc() {
        // 小程序 openid 自身无 PASS，但通过"微信手机号核验"建立了 identity_link 指向某自然人 hash。
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("mini-openid", KycStatus.PASS))
                .thenReturn(Optional.empty());
        when(linkRepo.findByOpenid("mini-openid"))
                .thenReturn(Optional.of(IdentityLink.phone("mini-openid", "hash-x")));
        when(kycRepo.findAllByIdCardHashAndStatus("hash-x", KycStatus.PASS))
                .thenReturn(List.of(rec("wx-openid", "hash-x")));
        when(linkRepo.findAllByIdCardHash("hash-x"))
                .thenReturn(List.of(IdentityLink.phone("mini-openid", "hash-x")));

        Set<String> owned = new IdentityResolver(kycRepo, linkRepo).resolveOwnedOpenids("mini-openid");

        assertThat(owned).containsExactlyInAnyOrder("mini-openid", "wx-openid");
    }

    @Test
    void degradesToSelfWhenNoVerifiedKyc() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("lonely", KycStatus.PASS))
                .thenReturn(Optional.empty());

        Set<String> owned = new IdentityResolver(kycRepo, linkRepo).resolveOwnedOpenids("lonely");

        assertThat(owned).containsExactly("lonely");
        verify(kycRepo, never()).findAllByIdCardHashAndStatus(any(), any());
    }

    @Test
    void passRecordWithNullHashDegradesToSelf_neverAggregates() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("o", KycStatus.PASS))
                .thenReturn(Optional.of(rec("o", null)));

        Set<String> owned = new IdentityResolver(kycRepo, linkRepo).resolveOwnedOpenids("o");

        assertThat(owned).containsExactly("o");
        verify(kycRepo, never()).findAllByIdCardHashAndStatus(any(), any());
    }

    @Test
    void blankOrNullOpenidReturnsEmpty() {
        IdentityResolver resolver = new IdentityResolver(kycRepo, linkRepo);
        assertThat(resolver.resolveOwnedOpenids("")).isEmpty();
        assertThat(resolver.resolveOwnedOpenids(null)).isEmpty();
    }
}

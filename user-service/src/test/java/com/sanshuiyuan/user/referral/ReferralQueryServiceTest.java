package com.sanshuiyuan.user.referral;

import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.infra.client.AssetServiceClient;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import com.sanshuiyuan.user.referral.api.MyReferralsResponse;
import com.sanshuiyuan.user.referral.api.ReferralItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ReferralQueryService 单测：购买状态由 mocked AssetServiceClient 提供。
 * 验证 purchased 标志驱动 status（PURCHASED/REGISTERED）、汇总计数及 status 过滤。
 */
@ExtendWith(MockitoExtension.class)
class ReferralQueryServiceTest {

    @Mock UserRepository userRepo;
    @Mock AssetServiceClient assetServiceClient;

    private ReferralQueryService service;

    private static final long ME = 100L;
    private static final long A = 1L; // 未购买
    private static final long B = 2L; // 已购买

    @BeforeEach
    void setUp() {
        service = new ReferralQueryService(userRepo, assetServiceClient);
    }

    private static User user(long id, String nickname) {
        User u = new User();
        u.setId(id);
        u.setNickname(nickname);
        return u;
    }

    @Test
    void purchasedFlagFromAssetService_drivesStatusAndCounts() {
        when(userRepo.findByInviterId(ME)).thenReturn(List.of(user(A, "alice"), user(B, "bob")));
        when(assetServiceClient.paidUserIds(any())).thenReturn(Set.of(B));

        MyReferralsResponse resp = service.myReferrals(ME, "ALL");

        assertThat(resp.summary().totalCount()).isEqualTo(2);
        assertThat(resp.summary().purchasedCount()).isEqualTo(1);
        assertThat(resp.summary().registeredCount()).isEqualTo(1);

        ReferralItemResponse itemA = resp.items().stream().filter(i -> i.userId() == A).findFirst().orElseThrow();
        ReferralItemResponse itemB = resp.items().stream().filter(i -> i.userId() == B).findFirst().orElseThrow();
        assertThat(itemA.status()).isEqualTo("REGISTERED");
        assertThat(itemB.status()).isEqualTo("PURCHASED");
    }

    @Test
    void purchasedFilter_returnsOnlyPurchasedUser() {
        when(userRepo.findByInviterId(ME)).thenReturn(List.of(user(A, "alice"), user(B, "bob")));
        when(assetServiceClient.paidUserIds(any())).thenReturn(Set.of(B));

        MyReferralsResponse resp = service.myReferrals(ME, "PURCHASED");

        // 计数始终基于全量（汇总不受过滤影响）；items 仅留已购买的 B。
        assertThat(resp.summary().purchasedCount()).isEqualTo(1);
        assertThat(resp.summary().registeredCount()).isEqualTo(1);
        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).userId()).isEqualTo(B);
        assertThat(resp.items().get(0).status()).isEqualTo("PURCHASED");
    }

    @Test
    void registeredFilter_returnsOnlyNonPurchasedUser() {
        when(userRepo.findByInviterId(ME)).thenReturn(List.of(user(A, "alice"), user(B, "bob")));
        when(assetServiceClient.paidUserIds(any())).thenReturn(Set.of(B));

        MyReferralsResponse resp = service.myReferrals(ME, "REGISTERED");

        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).userId()).isEqualTo(A);
        assertThat(resp.items().get(0).status()).isEqualTo("REGISTERED");
    }
}

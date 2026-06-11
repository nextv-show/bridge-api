package com.sanshuiyuan.cend.myorders.application;

import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.DeviceSpecRepository;
import com.sanshuiyuan.cend.identity.IdentityResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证"我的订单"按自然人聚合：列表查询使用 IdentityResolver 解析出的 openid 集合走 IN 查询，
 * 而非仅当前 openid——这是小程序能看到同人 H5 订单的关键。
 */
@ExtendWith(MockitoExtension.class)
class MyOrdersQueryServiceTest {

    @Mock CendOrderRepository orderRepo;
    @Mock DeviceSpecRepository specRepo;
    @Mock IdentityResolver identityResolver;

    @Test
    @SuppressWarnings("unchecked")
    void listQueriesByResolvedOpenidSet() {
        when(identityResolver.resolveOwnedOpenids("mini"))
                .thenReturn(new LinkedHashSet<>(List.of("mini", "wx")));
        when(specRepo.findAll()).thenReturn(List.of());
        when(orderRepo.findByOpenidInOrderByCreatedAtDesc(anySet(), any(Pageable.class)))
                .thenReturn(Page.<CendOrder>empty());

        new MyOrdersQueryService(orderRepo, specRepo, identityResolver).list("mini", 0, 20);

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(orderRepo).findByOpenidInOrderByCreatedAtDesc(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue()).containsExactlyInAnyOrder("mini", "wx");
    }
}

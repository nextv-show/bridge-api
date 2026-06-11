package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.api.dto.RechargeRecordDto;
import com.sanshuiyuan.asset.domain.WalletRecharge;
import com.sanshuiyuan.asset.infra.repository.ConsumerWalletRepository;
import com.sanshuiyuan.asset.infra.repository.WalletRechargeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 纯 Mockito 单测：WalletService.listRecharges。
 * 验证：① 按当前 userId 隔离查询（越权隔离契约）；② size 上限 50、page 下限 0；③ 映射为 DTO。
 */
@ExtendWith(MockitoExtension.class)
class WalletServiceListRechargesTest {

    @Mock ConsumerWalletRepository walletRepo;
    @Mock WalletRechargeRepository rechargeRepo;

    private WalletService service() {
        return new WalletService(walletRepo, rechargeRepo);
    }

    @Test
    void queriesByCurrentUserAndMapsToDto() {
        WalletRecharge r = WalletRecharge.create(42L, 20000L, 20, 0, "WECHAT");
        when(rechargeRepo.findByUserIdOrderByCreatedAtDesc(eq(42L), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r)));

        Page<RechargeRecordDto> out = service().listRecharges(42L, 0, 20);

        assertThat(out.getContent()).hasSize(1);
        assertThat(out.getContent().get(0).amountCents()).isEqualTo(20000L);
        assertThat(out.getContent().get(0).pointsGranted()).isEqualTo(20);
        assertThat(out.getContent().get(0).status()).isEqualTo("PENDING_PAY");
    }

    @Test
    void clampsSizeToFiftyAndPageToZero() {
        when(rechargeRepo.findByUserIdOrderByCreatedAtDesc(eq(7L), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(Page.empty());

        service().listRecharges(7L, -3, 999);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(rechargeRepo).findByUserIdOrderByCreatedAtDesc(eq(7L), cap.capture());
        assertThat(cap.getValue().getPageNumber()).isZero();
        assertThat(cap.getValue().getPageSize()).isEqualTo(50);
    }
}

package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.domain.DeviceAsset;
import com.sanshuiyuan.asset.infra.repository.DeviceAssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * D.3.1: 分页边界（默认 50 / 最大 200 / 非法回落）与 user_id 维度有序查询、SN 归属过滤。
 */
@ExtendWith(MockitoExtension.class)
class AssetQueryServiceTest {

    @Mock
    DeviceAssetRepository deviceAssetRepository;

    @InjectMocks
    AssetQueryService service;

    private Pageable capturePageable(Long userId, int page, int size) {
        when(deviceAssetRepository.findByUserId(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        service.getMyAssets(userId, page, size);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(deviceAssetRepository).findByUserId(eq(userId), captor.capture());
        return captor.getValue();
    }

    @Test
    void getMyAssets_defaultSize_is50_sortedByPurchasedAtDesc() {
        Pageable p = capturePageable(7L, 0, 50);
        assertThat(p.getPageSize()).isEqualTo(50);
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getSort().getOrderFor("purchasedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getMyAssets_sizeOverMax_clampedTo200() {
        assertThat(capturePageable(7L, 0, 10_000).getPageSize()).isEqualTo(200);
    }

    @Test
    void getMyAssets_nonPositiveSize_fallsBackToDefault() {
        assertThat(capturePageable(7L, 0, 0).getPageSize()).isEqualTo(50);
    }

    @Test
    void getMyAssets_negativePage_normalisedToZero() {
        assertThat(capturePageable(7L, -3, 50).getPageNumber()).isZero();
    }

    @Test
    void getMyAssets_returnsRepositoryPage() {
        DeviceAsset a = new DeviceAsset();
        a.setUserId(7L);
        Page<DeviceAsset> page = new PageImpl<>(List.of(a));
        when(deviceAssetRepository.findByUserId(eq(7L), any(Pageable.class))).thenReturn(page);

        assertThat(service.getMyAssets(7L, 0, 50).getContent()).hasSize(1);
    }

    @Test
    void getOwnedAsset_ownedByUser_returnsAsset() {
        DeviceAsset a = new DeviceAsset();
        a.setUserId(7L);
        a.setSn("SN-1");
        when(deviceAssetRepository.findBySn("SN-1")).thenReturn(Optional.of(a));

        assertThat(service.getOwnedAsset(7L, "SN-1")).containsSame(a);
    }

    @Test
    void getOwnedAsset_ownedByAnotherUser_filteredOut() {
        DeviceAsset a = new DeviceAsset();
        a.setUserId(7L);
        a.setSn("SN-1");
        when(deviceAssetRepository.findBySn("SN-1")).thenReturn(Optional.of(a));

        assertThat(service.getOwnedAsset(9L, "SN-1")).isEmpty();
    }

    @Test
    void getOwnedAsset_unknownSn_empty() {
        when(deviceAssetRepository.findBySn("nope")).thenReturn(Optional.empty());
        assertThat(service.getOwnedAsset(7L, "nope")).isEmpty();
    }
}

package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.domain.DeviceAsset;
import com.sanshuiyuan.asset.infra.repository.DeviceAssetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * D.3.1: 资产读取查询。集中分页边界（默认 50，最大 200）与 user_id 维度的有序查询，
 * 命中复合索引 device_assets(user_id, purchased_at)。
 */
@Service
public class AssetQueryService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;

    private final DeviceAssetRepository deviceAssetRepository;

    public AssetQueryService(DeviceAssetRepository deviceAssetRepository) {
        this.deviceAssetRepository = deviceAssetRepository;
    }

    public Page<DeviceAsset> getMyAssets(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        PageRequest pageable = PageRequest.of(safePage, clampSize(size),
                Sort.by("purchasedAt").descending());
        return deviceAssetRepository.findByUserId(userId, pageable);
    }

    public Optional<DeviceAsset> getOwnedAsset(Long userId, String sn) {
        return deviceAssetRepository.findBySn(sn)
                .filter(asset -> asset.getUserId().equals(userId));
    }

    /** size <= 0 回落默认 50；超过上限钳到 200，防止超大分页拖垮查询。 */
    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}

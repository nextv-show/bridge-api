package com.sanshuiyuan.asset.infra.repository;

import com.sanshuiyuan.asset.domain.DeviceAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceAssetRepository extends JpaRepository<DeviceAsset, Long> {
    List<DeviceAsset> findByUserId(Long userId);
    Optional<DeviceAsset> findBySn(String sn);
    Page<DeviceAsset> findByUserId(Long userId, Pageable pageable);
}

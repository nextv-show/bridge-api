package com.sanshuiyuan.settlement.infra.asset;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface DeviceAssetRepository extends JpaRepository<DeviceAssetEntity, Long> {

    Optional<DeviceAssetEntity> findBySn(String sn);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeviceAssetEntity> findWithLockBySn(String sn);

    Optional<DeviceAssetEntity> findBySnAndUserId(String sn, Long userId);
}

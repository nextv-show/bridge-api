package com.sanshuiyuan.settlement.infra.asset;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface DeviceAssetRepository extends JpaRepository<DeviceAssetEntity, Long> {

    Optional<DeviceAssetEntity> findBySn(String sn);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeviceAssetEntity> findWithLockBySn(String sn);

    Optional<DeviceAssetEntity> findBySnAndUserId(String sn, Long userId);

    /** 列出某用户名下全部设备资产（含运营中 STAGE_1/STAGE_2）。asset-service/asset_db 长期为空，
        此为小程序唯一能看到运营设备的数据源（/api/s/owner/assets）。 */
    List<DeviceAssetEntity> findByUserIdOrderByIdAsc(Long userId);
}

package com.sanshuiyuan.admin.infra.repository;

import com.sanshuiyuan.admin.domain.DeviceAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DeviceAssetRepository extends JpaRepository<DeviceAsset, Long> {

    Page<DeviceAsset> findBySnIsNullAndStage(DeviceAsset.Stage stage, Pageable pageable);

    boolean existsBySn(String sn);

    @Query("SELECT COUNT(d) FROM DeviceAsset d WHERE d.sn IS NOT NULL")
    long countBound();

    @Query("SELECT COUNT(d) FROM DeviceAsset d")
    long countTotal();
}

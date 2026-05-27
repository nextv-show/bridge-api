package com.sanshuiyuan.admin.infra.repository;

import com.sanshuiyuan.admin.domain.DeviceAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceAssetRepository extends JpaRepository<DeviceAsset, Long> {

    Page<DeviceAsset> findBySnIsNullAndStage(DeviceAsset.Stage stage, Pageable pageable);

    boolean existsBySn(String sn);

    Optional<DeviceAsset> findFirstByOrderId(Long orderId);

    @Query("SELECT COUNT(d) FROM DeviceAsset d WHERE d.sn IS NOT NULL")
    long countBound();

    @Query("SELECT COUNT(d) FROM DeviceAsset d")
    long countTotal();

    /** 按 Stage 分组计数。返回 [stage, count]。 */
    @Query("SELECT d.stage, COUNT(d) FROM DeviceAsset d GROUP BY d.stage")
    List<Object[]> countByStageGroup();

    /** 按 userId 批量统计设备数。返回 [userId, count]。 */
    @Query("SELECT d.userId, COUNT(d) FROM DeviceAsset d WHERE d.userId IN :ids GROUP BY d.userId")
    List<Object[]> countByUserIds(@Param("ids") Collection<Long> ids);

    List<DeviceAsset> findByUserIdOrderByPurchasedAtDesc(Long userId);
}

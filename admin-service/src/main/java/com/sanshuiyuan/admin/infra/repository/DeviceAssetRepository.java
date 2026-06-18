package com.sanshuiyuan.admin.infra.repository;

import com.sanshuiyuan.admin.domain.DeviceAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** FIFO 取 limit 台未绑定 + 指定 Stage 设备（按 id 升序）——批量自动分配 SN 用。 */
    @Query("SELECT d FROM DeviceAsset d WHERE d.stage = :stage AND d.sn IS NULL ORDER BY d.id ASC LIMIT :limit")
    List<DeviceAsset> findUnboundByStageOrderByIdAsc(@Param("stage") DeviceAsset.Stage stage, @Param("limit") int limit);

    /**
     * 条件更新（CAS）绑定 SN——仅当设备未绑 SN 且处于 PENDING_MATCH 时生效，消除 read-then-write 竞态。
     * JPQL 引用嵌套枚举需用全限定名 {@code DeviceAsset$Stage}。
     *
     * @return affected rows；0 表示已被并发绑定或状态已变更
     */
    @Modifying
    @Query("UPDATE DeviceAsset d SET d.sn = :sn WHERE d.id = :id AND d.sn IS NULL "
            + "AND d.stage = com.sanshuiyuan.admin.domain.DeviceAsset$Stage.PENDING_MATCH")
    int casBindSn(@Param("id") Long id, @Param("sn") String sn);
}

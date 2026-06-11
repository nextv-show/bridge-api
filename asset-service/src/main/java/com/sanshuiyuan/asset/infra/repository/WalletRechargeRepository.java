package com.sanshuiyuan.asset.infra.repository;

import com.sanshuiyuan.asset.domain.RechargeStatus;
import com.sanshuiyuan.asset.domain.WalletRecharge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletRechargeRepository extends JpaRepository<WalletRecharge, Long> {
    Optional<WalletRecharge> findByIdAndUserId(Long id, Long userId);

    /** 主动查单兜底用：拉取所有待支付充值单（ReconcileWalletRechargeJob）。 */
    List<WalletRecharge> findByStatus(RechargeStatus status);

    /**
     * 当前用户充值/账单流水（分页）。
     * 排序按「事件时间」降序：已支付按入账时间 paidAt、未支付按创建时间 createdAt（COALESCE），
     * 与前端展示时间戳一致；再以 id 降序作为同一时刻的确定性 tiebreaker，避免翻页重复/漏行。
     */
    @Query("SELECT r FROM WalletRecharge r WHERE r.userId = :userId "
            + "ORDER BY COALESCE(r.paidAt, r.createdAt) DESC, r.id DESC")
    Page<WalletRecharge> findHistoryByUserId(@Param("userId") Long userId, Pageable pageable);
}

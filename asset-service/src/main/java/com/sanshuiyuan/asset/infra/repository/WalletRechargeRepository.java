package com.sanshuiyuan.asset.infra.repository;

import com.sanshuiyuan.asset.domain.RechargeStatus;
import com.sanshuiyuan.asset.domain.WalletRecharge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WalletRechargeRepository extends JpaRepository<WalletRecharge, Long> {
    Optional<WalletRecharge> findByIdAndUserId(Long id, Long userId);

    /** 主动查单兜底用：拉取所有待支付充值单（ReconcileWalletRechargeJob）。 */
    List<WalletRecharge> findByStatus(RechargeStatus status);

    /** 当前用户充值/账单流水（按创建时间降序，分页）。 */
    Page<WalletRecharge> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}

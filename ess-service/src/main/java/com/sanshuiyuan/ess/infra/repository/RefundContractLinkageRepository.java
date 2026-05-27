package com.sanshuiyuan.ess.infra.repository;

import com.sanshuiyuan.ess.domain.RefundContractLinkage;
import com.sanshuiyuan.ess.domain.RefundContractLinkage.LinkageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 退款合同联动 Repository。
 */
@Repository
public interface RefundContractLinkageRepository extends JpaRepository<RefundContractLinkage, Long> {

    Optional<RefundContractLinkage> findByRefundOrderId(String refundOrderId);

    Optional<RefundContractLinkage> findBySupplementaryContractId(Long supplementaryContractId);

    boolean existsByRefundOrderId(String refundOrderId);

    boolean existsByRefundOrderIdAndLinkageStatus(String refundOrderId, LinkageStatus status);
}

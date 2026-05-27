package com.sanshuiyuan.ess.infra.repository;

import com.sanshuiyuan.ess.domain.SupplementaryContract;
import com.sanshuiyuan.ess.domain.SupplementaryContract.SupplementaryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 补充协议 Repository。
 */
@Repository
public interface SupplementaryContractRepository extends JpaRepository<SupplementaryContract, Long> {

    List<SupplementaryContract> findByOriginalContractId(Long originalContractId);

    Optional<SupplementaryContract> findByRefundOrderId(String refundOrderId);

    List<SupplementaryContract> findByOriginalContractIdAndStatus(Long originalContractId, SupplementaryStatus status);

    Optional<SupplementaryContract> findByEssFlowId(String essFlowId);

    List<SupplementaryContract> findByStatus(SupplementaryStatus status);

    boolean existsByRefundOrderId(String refundOrderId);
}

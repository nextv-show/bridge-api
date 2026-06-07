package com.sanshuiyuan.evidence.infra.repository;

import com.sanshuiyuan.evidence.domain.WaterBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WaterBillRepository extends JpaRepository<WaterBill, Long> {

    Optional<WaterBill> findById(Long id);

    @Modifying
    @Query("UPDATE WaterBill b SET b.chainTxHash = :txHash, "
            + "b.chainStatus = com.sanshuiyuan.evidence.domain.ChainStatus.ON_CHAIN, "
            + "b.chainRetried = :retried WHERE b.id = :id")
    int updateOnChain(@Param("id") Long id, @Param("txHash") String txHash, @Param("retried") int retried);

    @Modifying
    @Query("UPDATE WaterBill b SET b.chainStatus = com.sanshuiyuan.evidence.domain.ChainStatus.FAILED WHERE b.id = :id")
    int updateFailed(@Param("id") Long id);
}

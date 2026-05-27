package com.sanshuiyuan.ess.infra.repository;

import com.sanshuiyuan.ess.domain.ContractCooldownRecord;
import com.sanshuiyuan.ess.domain.ContractCooldownRecord.CooldownStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 冷静期记录 Repository。
 */
@Repository
public interface ContractCooldownRecordRepository extends JpaRepository<ContractCooldownRecord, Long> {

    Optional<ContractCooldownRecord> findByContractId(Long contractId);

    Optional<ContractCooldownRecord> findByOrderId(String orderId);

    List<ContractCooldownRecord> findByStatusAndCooldownEndAtBefore(CooldownStatus status, LocalDateTime time);

    List<ContractCooldownRecord> findByUserId(Long userId);

    List<ContractCooldownRecord> findByStatus(CooldownStatus status);

    boolean existsByContractIdAndStatus(Long contractId, CooldownStatus status);
}

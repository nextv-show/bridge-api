package com.sanshuiyuan.ess.infra.repository;

import com.sanshuiyuan.ess.domain.ContractAccessLog;
import com.sanshuiyuan.ess.domain.ContractAccessLog.AccessType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractAccessLogRepository extends JpaRepository<ContractAccessLog, Long> {

    List<ContractAccessLog> findByContractIdOrderByCreatedAtDesc(Long contractId);

    Page<ContractAccessLog> findByContractId(Long contractId, Pageable pageable);

    long countByContractIdAndAccessType(Long contractId, AccessType accessType);

    Page<ContractAccessLog> findByContractIdIn(List<Long> contractIds, Pageable pageable);
}

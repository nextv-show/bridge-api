package com.sanshuiyuan.ess.infra.repository;

import com.sanshuiyuan.ess.domain.ContractAuditTrail;
import com.sanshuiyuan.ess.domain.ContractAuditTrail.Action;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractAuditTrailRepository extends JpaRepository<ContractAuditTrail, Long> {

    Page<ContractAuditTrail> findByContractId(Long contractId, Pageable pageable);

    List<ContractAuditTrail> findByContractIdOrderByCreatedAtDesc(Long contractId);

    long countByContractIdAndAction(Long contractId, Action action);
}

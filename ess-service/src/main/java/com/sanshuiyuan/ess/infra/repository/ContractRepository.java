package com.sanshuiyuan.ess.infra.repository;

import com.sanshuiyuan.ess.domain.Contract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByContractNo(String contractNo);

    List<Contract> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Contract> findByOrderId(String orderId);

    Optional<Contract> findByDeviceSn(String deviceSn);

    Optional<Contract> findByEssFlowId(String essFlowId);

    List<Contract> findByStatus(Contract.ContractStatus status);
}

package com.sanshuiyuan.ess.infra.repository;

import com.sanshuiyuan.ess.domain.ContractSnBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractSnBindingRepository extends JpaRepository<ContractSnBinding, Long> {

    List<ContractSnBinding> findByContractId(Long contractId);

    Optional<ContractSnBinding> findByContractIdAndDeviceSn(Long contractId, String deviceSn);

    List<ContractSnBinding> findByDeviceSn(String deviceSn);

    List<ContractSnBinding> findByBindingType(ContractSnBinding.BindingType bindingType);
}

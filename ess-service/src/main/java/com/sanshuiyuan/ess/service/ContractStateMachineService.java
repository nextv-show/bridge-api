package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 合同状态机服务。
 * <p>
 * 管理合同状态流转：DRAFT → GENERATED → SIGNING → SIGNED → ARCHIVED。
 * 每次流转前校验合法性，防止非法跳转。
 */
@Service
public class ContractStateMachineService {

    private static final Logger log = LoggerFactory.getLogger(ContractStateMachineService.class);

    private final ContractRepository contractRepository;

    public ContractStateMachineService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    /**
     * 获取合同并校验当前状态。
     */
    @Transactional(readOnly = true)
    public Contract getContract(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: id=" + contractId));
    }

    /**
     * 转换到指定状态（带校验）。
     */
    @Transactional
    public Contract transition(Long contractId, ContractStatus targetStatus) {
        Contract contract = getContract(contractId);
        ContractStatus currentStatus = contract.getStatus();

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                    String.format("合同状态不允许从 %s 转换到 %s [contractNo=%s]",
                            currentStatus, targetStatus, contract.getContractNo()));
        }

        log.info("合同状态转换 [contractNo={}, {} → {}]",
                contract.getContractNo(), currentStatus, targetStatus);

        return contract;
    }

    /**
     * 获取合同当前状态。
     */
    @Transactional(readOnly = true)
    public ContractStatus getStatus(Long contractId) {
        return getContract(contractId).getStatus();
    }

    /**
     * 检查是否可以转换到指定状态。
     */
    @Transactional(readOnly = true)
    public boolean canTransition(Long contractId, ContractStatus targetStatus) {
        Contract contract = getContract(contractId);
        return contract.getStatus().canTransitionTo(targetStatus);
    }
}

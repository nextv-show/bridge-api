package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 签署中合同主动查单兜底任务（active-query fallback）。
 * <p>
 * 与微信支付/退款的回调零送达问题（参见 admin/feedback/h5-pay-active-query-fallback、
 * h5-refund-active-query-fallback）同源：腾讯电子签 webhook 实际到达率极低，
 * 多次实测线上 ess_flow_records.callback_data_json 长期为 NULL。
 * <p>
 * 本任务每 2 分钟扫描一次状态 = SIGNING 且有 essFlowId 的合同，主动调用
 * {@link EssContractService#describeFlowStatus(String)}（已修复响应解析），
 * 内部 updateRecordStatus 在 COMPLETED 时会自动通过 {@link ContractCompletionBridge}
 * 把 Contract 推进到 SIGNED 并触发归档。
 * <p>
 * 即使 webhook 全部丢失，签署完成最多延迟 2 分钟到账。
 */
@Service
public class ReconcileSigningContractsJob {

    private static final Logger log = LoggerFactory.getLogger(ReconcileSigningContractsJob.class);

    private final ContractRepository contractRepository;
    private final EssContractService essContractService;
    private final ContractCompletionBridge completionBridge;

    public ReconcileSigningContractsJob(ContractRepository contractRepository,
                                        EssContractService essContractService,
                                        ContractCompletionBridge completionBridge) {
        this.contractRepository = contractRepository;
        this.essContractService = essContractService;
        this.completionBridge = completionBridge;
    }

    /**
     * 主调度入口。{@code @Transactional} 不加在这里 —— 每条合同独立短事务，
     * 防止单条失败回滚整批；具体短事务交给 {@link EssContractService#describeFlowStatus}。
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void reconcile() {
        runOnce();
    }

    /**
     * 一次性扫描入口；同时被定时器和管理接口（手动重放）复用。
     *
     * @return 扫描的合同数量
     */
    public int runOnce() {
        List<Contract> signing = contractRepository.findByStatus(Contract.ContractStatus.SIGNING);
        if (signing.isEmpty()) {
            return 0;
        }
        log.info("[ReconcileSigning] 扫描到 {} 份 SIGNING 合同，开始主动查询 ESS", signing.size());

        int promoted = 0;
        for (Contract contract : signing) {
            if (contract.getEssFlowId() == null || contract.getEssFlowId().isBlank()) {
                continue; // 还没建流程的合同跳过
            }
            try {
                essContractService.describeFlowStatus(contract.getContractNo());
                // 二次保险：即便 describeFlowStatus 的 hook 因任何原因未触发桥接，
                // 这里直接读 EssFlowRecord 的 status 再 reconcile 一次（幂等）。
                // 注意：describeFlowStatus 已在内部 reload + updateRecordStatus；
                // 如果它把 record 推进到 COMPLETED 但 Contract 桥接失败，下面这步把它补上。
                if (completionBridge.bridgeToSigned(contract.getContractNo(), null)) {
                    promoted++;
                }
            } catch (Exception e) {
                log.warn("[ReconcileSigning] 单个合同处理失败 [contractNo={}]: {}",
                        contract.getContractNo(), e.getMessage());
            }
        }
        if (promoted > 0) {
            log.info("[ReconcileSigning] 本轮主动推进 {} 份合同到 SIGNED", promoted);
        }
        return signing.size();
    }
}

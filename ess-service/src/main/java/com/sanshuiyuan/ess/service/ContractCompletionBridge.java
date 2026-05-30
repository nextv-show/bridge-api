package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.domain.FlowStatus;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import com.sanshuiyuan.ess.infra.repository.EssFlowRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 把 {@link com.sanshuiyuan.ess.domain.EssFlowRecord} 的 COMPLETED 事件桥接到
 * {@link Contract} 状态机的服务。
 * <p>
 * 历史 bug：远端轮询（{@link EssContractService#describeFlowStatus}）和 Webhook 回调
 * （{@link EssCallbackService}）只更新 {@code EssFlowRecord.flowStatus}，从不推进
 * {@code Contract.status}，导致 H5 永远轮询 SIGNING。
 * <p>
 * 这里统一封装"按合同号桥接 → SIGNED + 触发归档"的逻辑，所有路径只调本桥接。
 * <p>
 * 设计：
 * - 通过 {@link ObjectProvider} 懒加载 {@link ContractSigningService}，避免与
 *   {@link EssContractService} 形成构造期循环依赖（后者被 ContractSigningService 注入）。
 * - 幂等：若 Contract 已经处于非 SIGNING 状态，直接返回 false 不再操作。
 * - PDF URL 通过 {@link EssDocumentService} 二次拉取；拿不到也允许 SIGNED 推进，
 *   归档环节会再次尝试拉取。
 */
@Service
public class ContractCompletionBridge {

    private static final Logger log = LoggerFactory.getLogger(ContractCompletionBridge.class);

    private final ContractRepository contractRepository;
    private final EssFlowRecordRepository flowRecordRepository;
    private final ObjectProvider<ContractSigningService> signingServiceProvider;
    private final ObjectProvider<EssDocumentService> documentServiceProvider;

    public ContractCompletionBridge(ContractRepository contractRepository,
                                     EssFlowRecordRepository flowRecordRepository,
                                     ObjectProvider<ContractSigningService> signingServiceProvider,
                                     ObjectProvider<EssDocumentService> documentServiceProvider) {
        this.contractRepository = contractRepository;
        this.flowRecordRepository = flowRecordRepository;
        this.signingServiceProvider = signingServiceProvider;
        this.documentServiceProvider = documentServiceProvider;
    }

    /**
     * 用合同业务编号触发 Contract 状态机推进到 SIGNED。
     * <p>
     * <b>关键不变量</b>：本方法不再「无条件信任调用方」。三个条件必须同时满足才会推进：
     * <ol>
     *   <li>Contract 存在且当前 status == SIGNING</li>
     *   <li>对应的 EssFlowRecord 存在</li>
     *   <li>EssFlowRecord.flowStatus == COMPLETED</li>
     * </ol>
     * 这样即使调用方误调（例如批量兜底任务对所有 SIGNING 合同一律调本方法），
     * 也绝不会把未签署的合同错误推进到 SIGNED。
     * <p>
     * PDF URL 在 {@code @Transactional} 边界 <b>之外</b> 拉取，避免 ESS API 异常
     * 把 JPA 会话标记为 rollback-only（首次部署观察到的 "Transaction silently rolled back" 警告）。
     *
     * @param contractNo 业务合同编号（=EssFlowRecord.contractId）
     * @param fallbackHashHint 兜底用的 hash 提示（如 webhook payload 自带 PdfHash 时传入），可为 null
     * @return true=成功桥接到 SIGNED；false=幂等跳过 / 目标不存在 / FlowRecord 还未 COMPLETED
     */
    public boolean bridgeToSigned(String contractNo, String fallbackHashHint) {
        // ---- 预检（无事务）：所有「不该桥接」的情况一律早退 ----
        Optional<Contract> contractOpt = contractRepository.findByContractNo(contractNo);
        if (contractOpt.isEmpty()) {
            log.warn("桥接 Contract 状态时找不到合同 [contractNo={}]", contractNo);
            return false;
        }
        Contract contract = contractOpt.get();
        if (contract.getStatus() != ContractStatus.SIGNING) {
            log.debug("Contract 不在 SIGNING，跳过桥接 [contractNo={}, status={}]",
                    contractNo, contract.getStatus());
            return false;
        }

        Optional<EssFlowRecord> recordOpt = flowRecordRepository.findByContractId(contractNo);
        if (recordOpt.isEmpty()) {
            log.warn("桥接时找不到 EssFlowRecord，跳过 [contractNo={}]", contractNo);
            return false;
        }
        FlowStatus flowStatus = recordOpt.get().getFlowStatus();
        if (flowStatus != FlowStatus.COMPLETED) {
            // 关键防御：ESS 远端没说 COMPLETED 就绝不推进。
            log.debug("FlowRecord 未 COMPLETED，跳过桥接 [contractNo={}, flowStatus={}]",
                    contractNo, flowStatus);
            return false;
        }

        // ---- 准备 PDF URL（无事务，失败不影响推进） ----
        String pdfUrl = tryResolvePdfUrl(contractNo);
        // pdfHash 占位为空，归档时 ContractArchiveService 会重新计算 SHA-256。
        String pdfHash = fallbackHashHint != null && !fallbackHashHint.isBlank()
                ? fallbackHashHint : "";

        // ---- 真正的状态机推进（短事务） ----
        return promoteToSigned(contract.getId(), contractNo, pdfUrl, pdfHash);
    }

    /**
     * 真正的状态机推进短事务。
     * <p>
     * 由 {@link #bridgeToSigned} 通过 Spring 代理调用以获得事务边界；外部勿直接调用
     * （除测试外，因为没有了任何前置防御）。
     */
    @Transactional
    public boolean promoteToSigned(Long contractId, String contractNo,
                                    String pdfUrl, String pdfHash) {
        try {
            // 通过 ContractSigningService 走一次完整的"签署完成"协议：
            //   状态机推进 + SN 绑定确认 + 自动触发归档（归档失败不影响 SIGNED）
            signingServiceProvider.getObject().completeSigning(contractId, pdfUrl, pdfHash);
            log.info("Contract 已桥接到 SIGNED [contractNo={}, contractId={}, hasPdfUrl={}]",
                    contractNo, contractId, pdfUrl != null && !pdfUrl.isBlank());
            return true;
        } catch (Exception e) {
            log.error("桥接 Contract → SIGNED 失败 [contractNo={}, contractId={}]: {}",
                    contractNo, contractId, e.getMessage(), e);
            // 不向上抛：远端同步/Webhook 主流程不应因桥接失败而失败，下一次轮询/兜底 Job 会再试。
            return false;
        }
    }

    /**
     * 安全地拉取签署完成 PDF 的 URL。拉失败返回 null（不影响主流程）。
     * <p>
     * 故意不开启事务 —— 上游远端 API 异常不应污染调用方的 JPA 会话
     * （触发 "Transaction silently rolled back"）。
     */
    private String tryResolvePdfUrl(String contractNo) {
        try {
            var urls = documentServiceProvider.getObject().getFileUrls(contractNo);
            if (urls != null && !urls.isEmpty()) {
                return urls.get(0);
            }
        } catch (Exception e) {
            log.warn("桥接时拉取 PDF URL 失败，将在归档阶段重试 [contractNo={}]: {}",
                    contractNo, e.getMessage());
        }
        return null;
    }
}

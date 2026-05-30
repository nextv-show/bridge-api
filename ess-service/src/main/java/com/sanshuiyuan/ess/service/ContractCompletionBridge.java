package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
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
    private final ObjectProvider<ContractSigningService> signingServiceProvider;
    private final ObjectProvider<EssDocumentService> documentServiceProvider;

    public ContractCompletionBridge(ContractRepository contractRepository,
                                     ObjectProvider<ContractSigningService> signingServiceProvider,
                                     ObjectProvider<EssDocumentService> documentServiceProvider) {
        this.contractRepository = contractRepository;
        this.signingServiceProvider = signingServiceProvider;
        this.documentServiceProvider = documentServiceProvider;
    }

    /**
     * 用合同业务编号触发 Contract 状态机推进到 SIGNED。
     * <p>
     * - 找不到合同：忽略并返回 false（避免误抛影响主流程，例如 webhook 处理）；
     * - 合同状态不是 SIGNING：幂等忽略，返回 false；
     * - 否则：拉取 PDF URL、计算 hash 占位（归档时会替换），调用 completeSigning。
     *
     * @param contractNo 业务合同编号（=EssFlowRecord.contractId）
     * @param fallbackHashHint 兜底用的 hash 提示（如 webhook payload 自带 PdfHash 时传入），可为 null
     * @return true=成功桥接到 SIGNED；false=幂等跳过或目标不存在
     */
    @Transactional
    public boolean bridgeToSigned(String contractNo, String fallbackHashHint) {
        Optional<Contract> opt = contractRepository.findByContractNo(contractNo);
        if (opt.isEmpty()) {
            log.warn("桥接 Contract 状态时找不到合同 [contractNo={}]", contractNo);
            return false;
        }
        Contract contract = opt.get();
        ContractStatus current = contract.getStatus();
        if (current != ContractStatus.SIGNING) {
            log.debug("Contract 不在 SIGNING，跳过桥接 [contractNo={}, status={}]", contractNo, current);
            return false;
        }

        String pdfUrl = tryResolvePdfUrl(contractNo);
        // pdfHash 在 ESS 同步阶段无法准确计算（需要下载文件后 SHA-256），
        // 这里用 hint 或空字符串占位；归档服务 ContractArchiveService 会重新计算并 updateArchiveUrls。
        String pdfHash = fallbackHashHint != null && !fallbackHashHint.isBlank()
                ? fallbackHashHint : "";

        try {
            // 通过 ContractSigningService 走一次完整的"签署完成"协议：
            //   状态机推进 + SN 绑定确认 + 自动触发归档（归档失败不影响 SIGNED）
            signingServiceProvider.getObject().completeSigning(contract.getId(), pdfUrl, pdfHash);
            log.info("Contract 已桥接到 SIGNED [contractNo={}, contractId={}, hasPdfUrl={}]",
                    contractNo, contract.getId(), pdfUrl != null && !pdfUrl.isBlank());
            return true;
        } catch (Exception e) {
            log.error("桥接 Contract → SIGNED 失败 [contractNo={}, contractId={}]: {}",
                    contractNo, contract.getId(), e.getMessage(), e);
            // 不向上抛：远端同步/Webhook 主流程不应因桥接失败而失败，下一次轮询/兜底 Job 会再试。
            return false;
        }
    }

    /**
     * 安全地拉取签署完成 PDF 的 URL。拉失败返回 null（不影响主流程）。
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

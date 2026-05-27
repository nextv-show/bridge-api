package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.RefundContractLinkage;
import com.sanshuiyuan.ess.infra.repository.RefundContractLinkageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 补充协议签署完成 → 退款状态联动通知。
 * <p>
 * 冷静期/补充协议/退款三方状态联动：
 * - 补充协议签署完成 → 更新联动状态为 SUPPLEMENTARY_SIGNED
 * - 通知退款系统退款可审批
 */
@Service
public class RefundLinkageNotifier {

    private static final Logger log = LoggerFactory.getLogger(RefundLinkageNotifier.class);

    private final RefundContractLinkageRepository linkageRepository;

    public RefundLinkageNotifier(RefundContractLinkageRepository linkageRepository) {
        this.linkageRepository = linkageRepository;
    }

    /**
     * 补充协议签署完成后通知退款系统。
     *
     * @param supplementaryContractId 补充协议 ID
     */
    @Transactional
    public void notifySupplementarySigned(Long supplementaryContractId) {
        linkageRepository.findBySupplementaryContractId(supplementaryContractId)
                .ifPresent(linkage -> {
                    if (linkage.getLinkageStatus() == RefundContractLinkage.LinkageStatus.PENDING) {
                        linkage.markSupplementarySigned();
                        linkageRepository.save(linkage);
                        log.info("联动状态更新为 SUPPLEMENTARY_SIGNED [scId={}, refundOrderId={}]",
                                supplementaryContractId, linkage.getRefundOrderId());

                        // TODO: 通知退款服务 API
                        notifyRefundService(linkage.getRefundOrderId(), "SUPPLEMENTARY_SIGNED");
                    }
                });
    }

    /**
     * 退款审批通过后更新联动状态。
     *
     * @param refundOrderId 退款订单 ID
     */
    @Transactional
    public void markRefundApproved(String refundOrderId) {
        linkageRepository.findByRefundOrderId(refundOrderId)
                .ifPresent(linkage -> {
                    linkage.markRefundApproved();
                    linkageRepository.save(linkage);
                    log.info("联动状态更新为 REFUND_APPROVED [refundOrderId={}]", refundOrderId);
                });
    }

    /**
     * 退款完成后更新联动状态。
     *
     * @param refundOrderId 退款订单 ID
     */
    @Transactional
    public void markRefundCompleted(String refundOrderId) {
        linkageRepository.findByRefundOrderId(refundOrderId)
                .ifPresent(linkage -> {
                    linkage.markRefundCompleted();
                    linkageRepository.save(linkage);
                    log.info("联动状态更新为 REFUND_COMPLETED [refundOrderId={}]", refundOrderId);
                });
    }

    /**
     * 通知退款服务。
     */
    private void notifyRefundService(String refundOrderId, String event) {
        log.info("通知退款服务 [refundOrderId={}, event={}]", refundOrderId, event);
        // TODO: 实际调用退款服务 API
    }
}

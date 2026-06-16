package com.sanshuiyuan.settlement.application.payout;

import com.sanshuiyuan.settlement.domain.WithdrawalOrder;
import com.sanshuiyuan.settlement.domain.WithdrawalSplit;
import com.sanshuiyuan.settlement.infra.repository.WithdrawalOrderRepository;
import com.sanshuiyuan.settlement.infra.repository.WithdrawalSplitRepository;
import com.sanshuiyuan.settlement.infra.wxpay.transfer.WxTransferBillsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 发起商家转账（在 {@code CreateWithdrawalUseCase.create} 提交后调用）。
 * 外部 HTTP 调用放在事务外；受理/失败的记账走 {@link PayoutMoneyOps} 的独立事务，
 * 避免「外部调用 + DB 写」同事务导致的资金/单据不一致。
 */
@Component
public class PayoutInitiationService {
    private static final Logger log = LoggerFactory.getLogger(PayoutInitiationService.class);

    private final WithdrawalOrderRepository orderRepository;
    private final WithdrawalSplitRepository splitRepository;
    private final OwnerOpenidResolver openidResolver;
    private final WxTransferBillsClient transferClient;
    private final PayoutMoneyOps moneyOps;

    public PayoutInitiationService(WithdrawalOrderRepository orderRepository,
                                   WithdrawalSplitRepository splitRepository,
                                   OwnerOpenidResolver openidResolver,
                                   WxTransferBillsClient transferClient,
                                   PayoutMoneyOps moneyOps) {
        this.orderRepository = orderRepository;
        this.splitRepository = splitRepository;
        this.openidResolver = openidResolver;
        this.transferClient = transferClient;
        this.moneyOps = moneyOps;
    }

    /** 发起结果。ok=false 时已退款（订单 FAILED）。 */
    public record InitiationResult(boolean ok, String packageInfo, String transferBillNo, String errorCode) {
        static InitiationResult fail(String code) { return new InitiationResult(false, null, null, code); }
    }

    public InitiationResult initiate(Long orderId) {
        WithdrawalOrder order = orderRepository.findById(orderId).orElseThrow();
        List<WithdrawalSplit> splits = splitRepository.findByOrderId(orderId);
        if (splits.isEmpty()) {
            moneyOps.refundOnFailure(orderId, "NO_SPLIT");
            return InitiationResult.fail("NO_SPLIT");
        }
        WithdrawalSplit split = splits.get(0);

        // 幂等：create 幂等返回旧单时，split 可能已发起/终结，勿重复调微信。
        switch (split.getStatus()) {
            case PAYING -> { return new InitiationResult(true, split.getPackageInfo(), split.getTransferBillNo(), null); }
            case PAID -> { return new InitiationResult(true, null, split.getTransferBillNo(), null); }
            case FAILED -> { return InitiationResult.fail("ALREADY_FAILED"); }
            default -> { /* QUEUED：继续发起 */ }
        }

        Optional<String> openid = openidResolver.findOpenid(order.getUserId());
        if (openid.isEmpty()) {
            log.warn("[payout] 无法解析 owner openid userId={}，退款 orderId={}", order.getUserId(), orderId);
            moneyOps.refundOnFailure(orderId, "OPENID_NOT_FOUND");
            return InitiationResult.fail("OPENID_NOT_FOUND");
        }

        String outBillNo = PayoutBillNo.of(orderId, split.getId());
        // 转账金额 = split.amountCents（净额，已扣手续费）。
        WxTransferBillsClient.TransferCommand cmd = new WxTransferBillsClient.TransferCommand(
                outBillNo, openid.get(), split.getAmountCents(), null, null);

        WxTransferBillsClient.InitiateResult r = transferClient.initiate(cmd); // 外部 HTTP，事务外
        if (r.accepted()) {
            moneyOps.recordAccepted(split.getId(), outBillNo, r.transferBillNo(), r.packageInfo());
            return new InitiationResult(true, r.packageInfo(), r.transferBillNo(), null);
        } else {
            moneyOps.refundOnFailure(orderId, "INITIATE_FAILED:" + r.errorCode());
            return InitiationResult.fail(r.errorCode());
        }
    }
}

package com.sanshuiyuan.settlement.application.payout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 微信商家转账回调处理。
 *
 * <p><b>安全设计</b>：回调体是 AES-GCM 加密的，且回调通道不可靠（本环境历史零送达）。
 * 因此本服务<b>不信任回调内容</b>（不以回调里的 state 直接解冻/退款，避免伪造 SUCCESS 骗解冻），
 * 仅把回调当作「催一次查单」的信号：将在途转账单的 nextRunAt 拨到当前，
 * 由 {@link PayoutWorker} 用<b>已鉴权的查单接口</b>确定真状态并落地。
 */
@Component
public class PayoutCallbackUseCase {
    private static final Logger log = LoggerFactory.getLogger(PayoutCallbackUseCase.class);

    private final PayoutMoneyOps moneyOps;

    public PayoutCallbackUseCase(PayoutMoneyOps moneyOps) {
        this.moneyOps = moneyOps;
    }

    public void handle(Map<String, Object> body) {
        log.info("[payout] 收到转账回调（仅触发查单，真状态以查单为准）eventType={}",
                body == null ? null : body.get("event_type"));
        moneyOps.nudgeAllInFlight();
    }
}

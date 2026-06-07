package com.sanshuiyuan.water.session.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.water.common.BizException;
import com.sanshuiyuan.water.common.ErrorCode;
import com.sanshuiyuan.water.session.domain.EndReason;
import com.sanshuiyuan.water.session.domain.EvidenceOutboxEntry;
import com.sanshuiyuan.water.session.domain.SessionStatus;
import com.sanshuiyuan.water.session.domain.WaterBill;
import com.sanshuiyuan.water.session.domain.WaterSession;
import com.sanshuiyuan.water.session.infra.EvidenceOutboxRepository;
import com.sanshuiyuan.water.session.infra.WaterBillRepository;
import com.sanshuiyuan.water.session.infra.WaterSessionRepository;
import com.sanshuiyuan.water.wallet.domain.ConsumerWallet;
import com.sanshuiyuan.water.wallet.domain.Direction;
import com.sanshuiyuan.water.wallet.domain.SourceType;
import com.sanshuiyuan.water.wallet.domain.WalletTransaction;
import com.sanshuiyuan.water.wallet.infra.ConsumerWalletRepository;
import com.sanshuiyuan.water.wallet.infra.WalletTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结算取水会话（单事务，幂等键=session_id）。流程：关闭会话 → 扣钱包 → 记流水 → 出账单 → 落存证发件箱。
 * 幂等保证：water_sessions WHERE status='ACTIVE' + water_bills.session_id UNIQUE
 * + wallet_transactions.uk_source + evidence_outbox.bill_id UNIQUE。
 */
@Service
public class SettleWaterSessionUseCase {

    private static final Logger log = LoggerFactory.getLogger(SettleWaterSessionUseCase.class);

    private final WaterSessionRepository sessionRepo;
    private final WaterBillRepository billRepo;
    private final EvidenceOutboxRepository outboxRepo;
    private final ConsumerWalletRepository walletRepo;
    private final WalletTransactionRepository txRepo;
    private final ObjectMapper objectMapper;

    public SettleWaterSessionUseCase(WaterSessionRepository sessionRepo, WaterBillRepository billRepo,
                                     EvidenceOutboxRepository outboxRepo, ConsumerWalletRepository walletRepo,
                                     WalletTransactionRepository txRepo, ObjectMapper objectMapper) {
        this.sessionRepo = sessionRepo;
        this.billRepo = billRepo;
        this.outboxRepo = outboxRepo;
        this.walletRepo = walletRepo;
        this.txRepo = txRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SettleResult settle(Long sessionId, long litersMilli, EndReason endReason) {
        WaterSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_NOT_FOUND));

        // 幂等：已结算（非 ACTIVE）→ 返回既有账单
        if (session.getStatus() != SessionStatus.ACTIVE) {
            return billRepo.findBySessionId(sessionId)
                    .map(b -> new SettleResult(b.getId(), sessionId, b.getLitersMilli(), b.getAmountCents(), true))
                    .orElseGet(() -> new SettleResult(null, sessionId,
                            session.getTotalLitersMilli(), session.getTotalAmountCents(), true));
        }

        int price = session.getPricePerLiterCents();
        long amountCents = litersMilli * price / 1000; // 向下取整

        // 计算实扣金额（BALANCE_OUT 兜底扣完）
        ConsumerWallet wallet = walletRepo.findByUserId(session.getUserId()).orElse(null);
        long balance = wallet != null ? wallet.getBalanceCents() : 0L;
        long deduct = amountCents;
        if (balance < amountCents) {
            if (endReason == EndReason.BALANCE_OUT) {
                deduct = Math.max(0L, balance);
            } else {
                throw new BizException(ErrorCode.LOW_BALANCE, "结算时余额不足，无法扣款");
            }
        }

        // 关闭会话（幂等：仅 ACTIVE + 版本匹配）
        final long finalDeduct = deduct;
        int closed = sessionRepo.closeActive(sessionId, litersMilli, finalDeduct, endReason.name(), session.getVersion());
        if (closed == 0) {
            return billRepo.findBySessionId(sessionId)
                    .map(b -> new SettleResult(b.getId(), sessionId, b.getLitersMilli(), b.getAmountCents(), true))
                    .orElseGet(() -> new SettleResult(null, sessionId, litersMilli, finalDeduct, true));
        }

        // 扣钱包（乐观锁）
        long balanceAfter = balance;
        if (deduct > 0) {
            if (wallet == null) {
                throw new BizException(ErrorCode.WALLET_NOT_FOUND);
            }
            wallet.credit(-deduct);
            walletRepo.save(wallet);
            balanceAfter = wallet.getBalanceCents();
        }

        // 记流水（uk_source 幂等）
        txRepo.save(WalletTransaction.of(session.getUserId(), Direction.OUT, SourceType.WATER_BILL,
                sessionId, deduct, balanceAfter));

        // 出账单（session_id UNIQUE 幂等）
        WaterBill bill = billRepo.save(WaterBill.of(session, litersMilli, deduct));

        // 落存证发件箱（bill_id UNIQUE 幂等）
        outboxRepo.save(EvidenceOutboxEntry.of(bill.getId(), buildPayload(bill), LocalDateTime.now()));

        log.info("会话结算完成 sessionId={} sn={} liters={} amount={} reason={}",
                sessionId, session.getSn(), litersMilli, deduct, endReason);
        return new SettleResult(bill.getId(), sessionId, litersMilli, deduct, false);
    }

    private String buildPayload(WaterBill bill) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bill_id", bill.getId());
        payload.put("session_id", bill.getSessionId());
        payload.put("sn", bill.getSn());
        payload.put("user_id", bill.getUserId());
        payload.put("liters_milli", bill.getLitersMilli());
        payload.put("price_per_liter_cents", bill.getPricePerLiterCents());
        payload.put("amount_cents", bill.getAmountCents());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("构建存证 payload 失败 billId={}", bill.getId(), e);
            return "{}";
        }
    }

    /** 结算结果。alreadySettled=true 表示幂等命中（会话此前已结算）。 */
    public record SettleResult(Long billId, Long sessionId, long litersMilli, long amountCents,
                               boolean alreadySettled) {}
}

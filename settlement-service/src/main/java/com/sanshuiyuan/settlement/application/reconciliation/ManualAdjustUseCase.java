package com.sanshuiyuan.settlement.application.reconciliation;

import com.sanshuiyuan.settlement.domain.*;
import com.sanshuiyuan.settlement.infra.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 人工调账（005 管理员操作，SVC token 鉴权）。
 * body: { user_id, amount_cents, direction: "IN"|"OUT", reason }
 * V1 仅写 owner_wallet + wallet_ledger，不反写 settlement_entries。
 */
@Component
public class ManualAdjustUseCase {
    private static final Logger log = LoggerFactory.getLogger(ManualAdjustUseCase.class);

    private final OwnerWalletRepository walletRepository;
    private final WalletLedgerRepository ledgerRepository;

    public ManualAdjustUseCase(OwnerWalletRepository walletRepository,
                               WalletLedgerRepository ledgerRepository) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public Map<String, Object> adjust(Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("user_id").toString());
        Long amountCents = Long.valueOf(body.get("amount_cents").toString());
        String direction = (String) body.get("direction");
        String reason = body.getOrDefault("reason", "MANUAL_ADJUST").toString();

        // FOR UPDATE 行锁，避免与结算/提现并发改余额
        OwnerWallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for user " + userId));

        long balanceBefore = wallet.getBalanceCents();

        if ("IN".equals(direction)) {
            wallet.setBalanceCents(balanceBefore + amountCents);
        } else if ("OUT".equals(direction)) {
            if (balanceBefore < amountCents) {
                throw new IllegalArgumentException("WALLET_INSUFFICIENT");
            }
            wallet.setBalanceCents(balanceBefore - amountCents);
        } else {
            throw new IllegalArgumentException("Invalid direction: " + direction);
        }

        walletRepository.save(wallet);

        // 写 ledger（source_id 用当前时间戳作为 adjust_id）
        long adjustId = System.currentTimeMillis();
        WalletLedger ledger = new WalletLedger(userId, LedgerDirection.valueOf(direction),
                LedgerSourceType.MANUAL_ADJUST, adjustId, amountCents, wallet.getBalanceCents());
        ledgerRepository.save(ledger);

        log.warn("[manual-adjust] userId={} direction={} amount={} reason={} balanceAfter={}",
                userId, direction, amountCents, reason, wallet.getBalanceCents());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_id", userId);
        result.put("direction", direction);
        result.put("amount_cents", amountCents);
        result.put("balance_after", wallet.getBalanceCents());
        return result;
    }
}

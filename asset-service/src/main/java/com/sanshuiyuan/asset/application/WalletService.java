package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.domain.ConsumerWallet;
import com.sanshuiyuan.asset.domain.RechargeStatus;
import com.sanshuiyuan.asset.domain.WalletRecharge;
import com.sanshuiyuan.asset.infra.repository.ConsumerWalletRepository;
import com.sanshuiyuan.asset.infra.repository.WalletRechargeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 水费钱包与充值用例。资金定性为预收账款，仅用于水费消费。
 * 起充门槛 ¥100；积分/水量配额按充值金额计提（与小程序档位一致）。
 */
@Service
public class WalletService {

    public static final long MIN_RECHARGE_CENTS = 10000L; // 起充 ¥100

    private final ConsumerWalletRepository walletRepo;
    private final WalletRechargeRepository rechargeRepo;

    public WalletService(ConsumerWalletRepository walletRepo, WalletRechargeRepository rechargeRepo) {
        this.walletRepo = walletRepo;
        this.rechargeRepo = rechargeRepo;
    }

    @Transactional
    public ConsumerWallet getOrCreate(Long userId) {
        return walletRepo.findById(userId)
                .orElseGet(() -> walletRepo.save(ConsumerWallet.createFor(userId)));
    }

    @Transactional
    public WalletRecharge createRecharge(Long userId, long amountCents, int points, int liters, String payChannel) {
        if (amountCents < MIN_RECHARGE_CENTS) {
            throw new IllegalArgumentException("起充金额不得低于 ¥100");
        }
        getOrCreate(userId); // 确保钱包存在
        return rechargeRepo.save(WalletRecharge.create(userId, amountCents, points, liters, payChannel));
    }

    @Transactional(readOnly = true)
    public WalletRecharge getOwnedRecharge(Long userId, Long rechargeId) {
        return rechargeRepo.findByIdAndUserId(rechargeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("充值单不存在"));
    }

    /**
     * 标记充值已支付并入账钱包。幂等：仅当处于 PENDING_PAY 时执行入账。
     * 生产环境由微信支付回调触发；dev 由 WalletSimulateController 触发。
     */
    @Transactional
    public WalletRecharge markPaidAndCredit(Long userId, Long rechargeId, String txnId) {
        WalletRecharge r = rechargeRepo.findByIdAndUserId(rechargeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("充值单不存在"));
        if (r.getStatus() == RechargeStatus.PAID) {
            return r; // 幂等
        }
        if (r.getStatus() != RechargeStatus.PENDING_PAY) {
            throw new IllegalStateException("充值单状态不可支付：" + r.getStatus());
        }
        return creditPaid(r, txnId);
    }

    /**
     * 微信支付回调入账：按 rechargeId 定位（回调无 userId，从充值单取）。幂等。
     */
    @Transactional
    public WalletRecharge markPaidByRecharge(Long rechargeId, String txnId) {
        WalletRecharge r = rechargeRepo.findById(rechargeId)
                .orElseThrow(() -> new IllegalArgumentException("充值单不存在"));
        if (r.getStatus() == RechargeStatus.PAID) {
            return r; // 幂等
        }
        if (r.getStatus() != RechargeStatus.PENDING_PAY) {
            throw new IllegalStateException("充值单状态不可支付：" + r.getStatus());
        }
        return creditPaid(r, txnId);
    }

    private WalletRecharge creditPaid(WalletRecharge r, String txnId) {
        r.markPaid(txnId != null ? txnId : "SIMULATE-" + UUID.randomUUID().toString().substring(0, 16));
        rechargeRepo.save(r);

        ConsumerWallet wallet = getOrCreate(r.getUserId());
        wallet.credit(r.getAmountCents(), r.getPointsGranted(), r.getLitersGranted());
        walletRepo.save(wallet);
        return r;
    }
}

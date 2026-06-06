package com.sanshuiyuan.water.wallet.application;

import com.sanshuiyuan.water.wallet.domain.ConsumerWallet;
import com.sanshuiyuan.water.wallet.domain.Direction;
import com.sanshuiyuan.water.wallet.domain.PaymentInbox;
import com.sanshuiyuan.water.wallet.domain.SourceType;
import com.sanshuiyuan.water.wallet.domain.TopupStatus;
import com.sanshuiyuan.water.wallet.domain.WalletTopup;
import com.sanshuiyuan.water.wallet.domain.WalletTransaction;
import com.sanshuiyuan.water.wallet.infra.ConsumerWalletRepository;
import com.sanshuiyuan.water.wallet.infra.PaymentInboxRepository;
import com.sanshuiyuan.water.wallet.infra.WalletTopupRepository;
import com.sanshuiyuan.water.wallet.infra.WalletTransactionRepository;
import com.sanshuiyuan.water.wallet.infra.wxpay.WxPayCallbackVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 充值回调落账。验签通过后单事务内幂等处理：收件箱去重 → 改充值单为 PAID → 钱包加余额（乐观锁/UPSERT）→ 记入账流水。
 */
@Service
public class TopupCallbackUseCase {

    private static final Logger log = LoggerFactory.getLogger(TopupCallbackUseCase.class);

    private final WxPayCallbackVerifier verifier;
    private final WalletTopupRepository topupRepo;
    private final PaymentInboxRepository inboxRepo;
    private final ConsumerWalletRepository walletRepo;
    private final WalletTransactionRepository txnRepo;

    public TopupCallbackUseCase(WxPayCallbackVerifier verifier, WalletTopupRepository topupRepo,
                                PaymentInboxRepository inboxRepo, ConsumerWalletRepository walletRepo,
                                WalletTransactionRepository txnRepo) {
        this.verifier = verifier;
        this.topupRepo = topupRepo;
        this.inboxRepo = inboxRepo;
        this.walletRepo = walletRepo;
        this.txnRepo = txnRepo;
    }

    /** 真实微信回调入口：先验签，再落账。 */
    public String handleCallback(String body, String signature, String timestamp, String nonce, String serial) {
        WxPayCallbackVerifier.VerifyResult result = verifier.verifyAndDecrypt(body, signature, timestamp, nonce, serial);
        if (!result.valid()) {
            return "FAIL";
        }
        // 仅 SUCCESS 才落账；其它状态 ack 但不改单（幂等返回 SUCCESS 避免微信重试）。
        if (result.tradeState() != null && !"SUCCESS".equals(result.tradeState())) {
            log.warn("充值回调 tradeState={} 非 SUCCESS，幂等返回不落账 outTradeNo={}",
                    result.tradeState(), result.outTradeNo());
            return "SUCCESS";
        }
        return applyPaid(result.outTradeNo(), result.transactionId(), result.rawBody());
    }

    /**
     * 标记充值单已支付并落账（幂等）。真实回调与 dev simulate 共用此入口。
     */
    @Transactional
    public String applyPaid(String outTradeNo, String wxTransactionId, String rawBody) {
        var topupOpt = topupRepo.findByOutTradeNo(outTradeNo);
        if (topupOpt.isEmpty()) {
            log.warn("充值单不存在 outTradeNo={}", outTradeNo);
            return "FAIL";
        }
        WalletTopup topup = topupOpt.get();

        // 1) 幂等：同一微信交易号已处理则直接返回成功。
        if (inboxRepo.findByTransactionId(wxTransactionId).isPresent()) {
            log.info("充值回调幂等命中 wxTransactionId={} outTradeNo={}", wxTransactionId, outTradeNo);
            return "SUCCESS";
        }
        // 充值单已是终态（PAID）也视为幂等成功。
        if (topup.getStatus() == TopupStatus.PAID) {
            log.info("充值单已支付，幂等返回 outTradeNo={}", outTradeNo);
            return "SUCCESS";
        }

        // 2) 写收件箱（唯一键兜底并发重复回调）。
        inboxRepo.save(PaymentInbox.create(wxTransactionId, outTradeNo, rawBody));

        // 3) 改充值单为 PAID。
        topup.markPaid(wxTransactionId, LocalDateTime.now());
        topupRepo.save(topup);

        // 4) UPSERT 钱包余额。
        ConsumerWallet wallet = walletRepo.findByUserId(topup.getUserId())
                .orElseGet(() -> ConsumerWallet.create(topup.getUserId(), 0L));
        wallet.credit(topup.getAmountCents());
        wallet = walletRepo.save(wallet);

        // 5) 记入账流水。
        txnRepo.save(WalletTransaction.of(
                topup.getUserId(), Direction.IN, SourceType.TOPUP, topup.getId(),
                topup.getAmountCents(), wallet.getBalanceCents()));

        log.info("充值落账成功 topupId={} userId={} amount={} balanceAfter={}",
                topup.getId(), topup.getUserId(), topup.getAmountCents(), wallet.getBalanceCents());
        return "SUCCESS";
    }
}

package com.sanshuiyuan.settlement.application;

import com.sanshuiyuan.settlement.domain.*;
import com.sanshuiyuan.settlement.infra.asset.DeviceAssetEntity;
import com.sanshuiyuan.settlement.infra.asset.DeviceAssetRepository;
import com.sanshuiyuan.settlement.infra.repository.*;
import com.sanshuiyuan.settlement.infra.water.WaterBillEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 分账落账核心：把一笔已结算的 water_db.water_bills 账单按受益人维度拆分，
 * 写 settlement_entries + owner_wallets + wallet_ledger，并在累计 ROI 跨过回本线时推进设备阶段。
 *
 * <p>FR-1：单事务原子落账。幂等性由 (bill_id, beneficiary_type, beneficiary_user_id) 唯一键保证，
 * 并在入口处先用 {@code findByBillId} 短路重复账单，避免重复处理与约束冲突。
 */
@Component
public class SettlePostingUseCase {
    private static final Logger log = LoggerFactory.getLogger(SettlePostingUseCase.class);

    private static final int STAGE_1_OWNER_BP = 8500;
    private static final int STAGE_2_OWNER_BP = 4500;

    private final DeviceAssetRepository deviceAssetRepository;
    private final SettlementEntryRepository settlementEntryRepository;
    private final OwnerWalletRepository ownerWalletRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final DeviceStageHistoryRepository deviceStageHistoryRepository;
    private final SettlementOutboxRepository settlementOutboxRepository;

    /** ENTRY_POSTED outbox 事件可配置关闭（默认关闭），避免高频账单写爆 outbox。 */
    private final boolean entriesPostedOutboxEnabled;

    public SettlePostingUseCase(DeviceAssetRepository deviceAssetRepository,
                                SettlementEntryRepository settlementEntryRepository,
                                OwnerWalletRepository ownerWalletRepository,
                                WalletLedgerRepository walletLedgerRepository,
                                DeviceStageHistoryRepository deviceStageHistoryRepository,
                                SettlementOutboxRepository settlementOutboxRepository,
                                @Value("${settlement.outbox-entries-posted-enabled:false}") boolean entriesPostedOutboxEnabled) {
        this.deviceAssetRepository = deviceAssetRepository;
        this.settlementEntryRepository = settlementEntryRepository;
        this.ownerWalletRepository = ownerWalletRepository;
        this.walletLedgerRepository = walletLedgerRepository;
        this.deviceStageHistoryRepository = deviceStageHistoryRepository;
        this.settlementOutboxRepository = settlementOutboxRepository;
        this.entriesPostedOutboxEnabled = entriesPostedOutboxEnabled;
    }

    /**
     * FR-1: 分账落账，单事务保证原子性。
     * @param bill 来自 water_db.water_bills 的已结算账单
     */
    @Transactional
    public void post(WaterBillEntity bill) {
        // 0. 幂等短路：同一 bill_id 已分账则直接返回（重复轮询/重放安全）。
        if (!settlementEntryRepository.findByBillId(bill.getId()).isEmpty()) {
            log.debug("Bill {} already settled, skipping", bill.getId());
            return;
        }

        // 1. 读设备快照（FOR UPDATE 行锁）
        DeviceAssetEntity asset = deviceAssetRepository.findWithLockBySn(bill.getSn())
                .orElse(null);

        if (asset == null) {
            // SN 不在 device_assets — 全额归平台，写错误日志
            createPlatformOnlyEntry(bill, "SN_NOT_FOUND", "PENDING_MATCH");
            log.error("SN {} not found in device_assets, bill {} settled to platform only", bill.getSn(), bill.getId());
            return;
        }

        // 2. 阶段判定
        DeviceStage stage = asset.getStage();
        long amountCents = bill.getAmountCents();

        if (stage == DeviceStage.PENDING_MATCH || stage == DeviceStage.SELF_USE
                || stage == DeviceStage.LOCKED || stage == DeviceStage.PENDING_ACTIVATE) {
            // FR-1.3: pre-install 阶段（含 SELF_USE/LOCKED）全额归平台，不改所有权人钱包
            createPlatformOnlyEntry(bill, "BLOCKED_PRE_INSTALL", stage.name());
            return;
        }

        // 3. 确定 owner_bp（STAGE_1=85%, STAGE_2=45%）
        int ownerBp = (stage == DeviceStage.STAGE_2) ? STAGE_2_OWNER_BP : STAGE_1_OWNER_BP;
        int promoterBp = 0;  // V1 无推广者
        int platformBp = 10000 - ownerBp - promoterBp;

        // 4. 拆分计算（余数归平台）
        long ownerAmount = Math.round(amountCents * ownerBp / 10000.0);
        long promoterAmount = 0L;  // V1
        long platformAmount = amountCents - ownerAmount - promoterAmount;

        // 5. 写 settlement_entries（OWNER + PROMOTER 0元 + PLATFORM）
        Long ownerUserId = asset.getUserId();

        SettlementEntry ownerEntry = new SettlementEntry(bill.getId(), bill.getSn(), BeneficiaryType.OWNER,
                ownerUserId, ownerAmount, ownerBp, promoterBp, platformBp,
                SettlementSplitReason.NORMAL, stage);
        settlementEntryRepository.save(ownerEntry);

        // PROMOTER 0 元 entry（V1 验证链路，G-7 已决议保留）
        SettlementEntry promoterEntry = new SettlementEntry(bill.getId(), bill.getSn(), BeneficiaryType.PROMOTER,
                null, 0L, ownerBp, promoterBp, platformBp,
                SettlementSplitReason.NORMAL, stage);
        settlementEntryRepository.save(promoterEntry);

        // PLATFORM
        SettlementEntry platformEntry = new SettlementEntry(bill.getId(), bill.getSn(), BeneficiaryType.PLATFORM,
                null, platformAmount, ownerBp, promoterBp, platformBp,
                SettlementSplitReason.NORMAL, stage);
        settlementEntryRepository.save(platformEntry);

        // 6. 更新 owner_wallet（FOR UPDATE 行锁）
        OwnerWallet wallet = ownerWalletRepository.findByUserId(ownerUserId)
                .orElseGet(() -> ownerWalletRepository.save(new OwnerWallet(ownerUserId, 0L, 0L)));

        wallet.setBalanceCents(wallet.getBalanceCents() + ownerAmount);
        ownerWalletRepository.save(wallet);

        long balanceAfter = wallet.getBalanceCents();

        // 7. 写 wallet_ledger（IN, SETTLEMENT）
        WalletLedger ledger = new WalletLedger(ownerUserId, LedgerDirection.IN, LedgerSourceType.SETTLEMENT,
                ownerEntry.getId(), ownerAmount, balanceAfter);
        walletLedgerRepository.save(ledger);

        // 8. 更新 device_assets（累计收益 + ROI）
        long newCumulativeIncome = asset.getCumulativeIncomeCents() + ownerAmount;
        asset.setCumulativeIncomeCents(newCumulativeIncome);

        long purchasePrice = asset.getPurchasePriceCents();
        int newRoiBp = (purchasePrice > 0)
                ? (int) (newCumulativeIncome * 10000 / purchasePrice)
                : 0;
        int oldRoiBp = asset.getRoiBp();
        asset.setRoiBp(newRoiBp);

        // 9. 阶段切换判断（FR-2.2: 首次跨过 100% 回本线）
        boolean crossedRoiThreshold = (oldRoiBp < 10000 && newRoiBp >= 10000);
        if (crossedRoiThreshold && stage == DeviceStage.STAGE_1) {
            asset.setStage(DeviceStage.STAGE_2);

            // 写 device_stage_history
            DeviceStageHistory history = new DeviceStageHistory(bill.getSn(), stage, DeviceStage.STAGE_2,
                    bill.getId(), newRoiBp);
            deviceStageHistoryRepository.save(history);

            // 写 settlement_outbox STAGE_CHANGED
            String idempotencyKey = "STAGE_CHANGED:" + bill.getSn() + ":STAGE_2:" + bill.getId();
            writeOutbox("DEVICE", bill.getSn(), OutboxEventType.STAGE_CHANGED, idempotencyKey,
                    "{\"sn\":\"" + bill.getSn() + "\",\"from_stage\":\"" + stage
                    + "\",\"to_stage\":\"STAGE_2\",\"at_bill_id\":" + bill.getId()
                    + ",\"at_roi_bp\":" + newRoiBp + "}");
        }

        deviceAssetRepository.save(asset);

        // 10. 写 ENTRY_POSTED outbox（可配置关闭）
        if (entriesPostedOutboxEnabled) {
            String entryKey = "ENTRY_POSTED:" + ownerEntry.getId();
            writeOutbox("ENTRY", String.valueOf(ownerEntry.getId()), OutboxEventType.ENTRY_POSTED, entryKey,
                    "{\"entry_id\":" + ownerEntry.getId() + ",\"bill_id\":" + bill.getId()
                    + ",\"sn\":\"" + bill.getSn() + "\",\"beneficiary_type\":\"OWNER\""
                    + ",\"beneficiary_user_id\":" + ownerUserId
                    + ",\"amount_cents\":" + ownerAmount + "}");
        }

        log.info("Bill {} settled: owner={} cents, platform={} cents, stage={}, roi={}bp",
                bill.getId(), ownerAmount, platformAmount, asset.getStage(), newRoiBp);
    }

    private void createPlatformOnlyEntry(WaterBillEntity bill, String splitReason, String stageAtPost) {
        SettlementEntry entry = new SettlementEntry(bill.getId(), bill.getSn(), BeneficiaryType.PLATFORM,
                null, bill.getAmountCents(), 0, 0, 10000,
                SettlementSplitReason.valueOf(splitReason), DeviceStage.valueOf(stageAtPost));
        settlementEntryRepository.save(entry);
    }

    private void writeOutbox(String aggregateType, String aggregateId,
                             OutboxEventType eventType, String idempotencyKey, String payloadJson) {
        try {
            SettlementOutbox outbox = new SettlementOutbox(aggregateType, aggregateId, eventType, payloadJson,
                    idempotencyKey, OutboxStatus.PENDING, 0, LocalDateTime.now());
            settlementOutboxRepository.save(outbox);
        } catch (Exception e) {
            // 幂等键冲突不抛异常
            log.debug("Outbox idempotency key {} already exists", idempotencyKey);
        }
    }
}

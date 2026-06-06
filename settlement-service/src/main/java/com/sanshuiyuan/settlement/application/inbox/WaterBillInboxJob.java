package com.sanshuiyuan.settlement.application.inbox;

import com.sanshuiyuan.settlement.application.SettlePostingUseCase;
import com.sanshuiyuan.settlement.domain.SettlementInboxCursor;
import com.sanshuiyuan.settlement.infra.repository.SettlementInboxCursorRepository;
import com.sanshuiyuan.settlement.infra.water.WaterBillEntity;
import com.sanshuiyuan.settlement.infra.water.WaterBillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 增量轮询 water_db.water_bills，按 id 游标推进，逐条调用 {@link SettlePostingUseCase#post}。
 *
 * <p>本方法<strong>不</strong>开启事务：每条账单由 {@code post}（@Transactional）独立落账，
 * 单条失败不会污染其它账单或游标推进；失败行记录错误日志，由 005 对账兜底。
 */
@Component
public class WaterBillInboxJob {
    private static final Logger log = LoggerFactory.getLogger(WaterBillInboxJob.class);

    private final WaterBillRepository waterBillRepository;
    private final SettlementInboxCursorRepository cursorRepository;
    private final SettlePostingUseCase settlePostingUseCase;

    public WaterBillInboxJob(WaterBillRepository waterBillRepository,
                             SettlementInboxCursorRepository cursorRepository,
                             SettlePostingUseCase settlePostingUseCase) {
        this.waterBillRepository = waterBillRepository;
        this.cursorRepository = cursorRepository;
        this.settlePostingUseCase = settlePostingUseCase;
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        SettlementInboxCursor cursor = cursorRepository.findByName("water_bill")
                .orElseGet(() -> cursorRepository.save(new SettlementInboxCursor("water_bill")));

        List<WaterBillEntity> bills = waterBillRepository
                .findByIdGreaterThanOrderByIdAsc(cursor.getLastId(), PageRequest.of(0, 500));

        if (bills.isEmpty()) return;

        long maxId = cursor.getLastId();
        for (WaterBillEntity bill : bills) {
            try {
                settlePostingUseCase.post(bill);
                maxId = Math.max(maxId, bill.getId());
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 幂等：同 bill_id 已分账，推进 cursor 继续
                log.info("Bill {} already settled (duplicate key), advancing cursor", bill.getId());
                maxId = Math.max(maxId, bill.getId());
            } catch (Exception e) {
                // 失败行不阻塞 cursor 推进 — 记录错误由 005 兜底
                log.error("Failed to settle bill {} (sn={})", bill.getId(), bill.getSn(), e);
                maxId = Math.max(maxId, bill.getId());
            }
        }
        cursor.setLastId(maxId);
        cursorRepository.save(cursor);
    }
}

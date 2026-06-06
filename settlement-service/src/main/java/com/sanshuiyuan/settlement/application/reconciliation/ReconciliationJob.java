package com.sanshuiyuan.settlement.application.reconciliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanshuiyuan.settlement.domain.*;
import com.sanshuiyuan.settlement.infra.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 每日对账作业（plan §6.5）：比对 water_db.water_bills 当日结算总额与
 * settlement_entries 各受益人维度落账总额，差额不为 0 时落 reconciliation_alert
 * 并写 settlement_outbox(RECONCILE_FAILED) 通知 005-admin。
 *
 * <p>对账平时写一条 RESOLVED alert 留痕。reconciliation_alerts.date 唯一键保证
 * 重跑不会重复落账（重复写在 catch 中吞掉，等待人工/下次重跑）。
 */
@Component
public class ReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final JdbcTemplate jdbc;
    private final ReconciliationAlertRepository alertRepository;
    private final SettlementOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReconciliationJob(JdbcTemplate jdbc,
                             ReconciliationAlertRepository alertRepository,
                             SettlementOutboxRepository outboxRepository) {
        this.jdbc = jdbc;
        this.alertRepository = alertRepository;
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Shanghai")
    @Transactional
    public void runDaily() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("[reconciliation] starting for date={}", yesterday);

        try {
            // 跨库查询 water_db.water_bills 当日已结算账单总额
            Long billsTotal = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount_cents), 0) FROM water_db.water_bills WHERE DATE(settled_at) = ?",
                    Long.class, yesterday);

            Long ownerTotal = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount_cents), 0) FROM settlement_entries WHERE beneficiary_type = 'OWNER' AND DATE(posted_at) = ?",
                    Long.class, yesterday);
            Long promoterTotal = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount_cents), 0) FROM settlement_entries WHERE beneficiary_type = 'PROMOTER' AND DATE(posted_at) = ?",
                    Long.class, yesterday);
            Long platformTotal = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount_cents), 0) FROM settlement_entries WHERE beneficiary_type = 'PLATFORM' AND DATE(posted_at) = ?",
                    Long.class, yesterday);

            long billsCents = billsTotal != null ? billsTotal : 0;
            long ownerCents = ownerTotal != null ? ownerTotal : 0;
            long promoterCents = promoterTotal != null ? promoterTotal : 0;
            long platformCents = platformTotal != null ? platformTotal : 0;

            long entriesTotal = ownerCents + promoterCents + platformCents;
            long diff = billsCents - entriesTotal;

            if (diff == 0) {
                // 对账平：写 RESOLVED alert 留痕
                ReconciliationAlert alert = new ReconciliationAlert(
                        yesterday, 0L, "{\"result\":\"RECONCILED\"}", ReconciliationStatus.RESOLVED);
                alert.setResolvedAt(LocalDateTime.now());
                alertRepository.save(alert);
                log.info("[reconciliation] {} RECONCILED (bills={} entries={})", yesterday, billsCents, entriesTotal);
            } else {
                // 差额 → 写 OPEN alert + outbox RECONCILE_FAILED
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("date", yesterday.toString());
                payload.put("bills_total", billsCents);
                payload.put("owner_total", ownerCents);
                payload.put("promoter_total", promoterCents);
                payload.put("platform_total", platformCents);
                payload.put("diff_cents", diff);
                String payloadJson = objectMapper.writeValueAsString(payload);

                ReconciliationAlert alert = new ReconciliationAlert(
                        yesterday, diff, payloadJson, ReconciliationStatus.OPEN);
                alertRepository.save(alert);

                SettlementOutbox outbox = new SettlementOutbox(
                        "RECONCILE", yesterday.toString(), OutboxEventType.RECONCILE_FAILED, payloadJson,
                        "RECONCILE_FAILED:" + yesterday, OutboxStatus.PENDING, 0, LocalDateTime.now());
                outboxRepository.save(outbox);

                log.warn("[reconciliation] {} MISMATCH diff={} (bills={} entries={})",
                        yesterday, diff, billsCents, entriesTotal);
            }
        } catch (Exception e) {
            log.error("[reconciliation] job failed for date={}", yesterday, e);
            // 兜底：下次重跑（date UNIQUE 保证不重复写 alert）
        }
    }
}

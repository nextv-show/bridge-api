package com.sanshuiyuan.water.session.application;

import com.sanshuiyuan.water.device.infra.IotGatewayClient;
import com.sanshuiyuan.water.session.domain.EndReason;
import com.sanshuiyuan.water.session.domain.WaterSession;
import com.sanshuiyuan.water.session.infra.WaterSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时兜底结算。每 30s 扫描超过 {@code water.session-timeout-minutes} 仍 ACTIVE 的会话，
 * 下发 stop 并以 TIMEOUT 结算（V1 stub：液量取 water_sessions.total_liters_milli，不回查 telemetry）。
 */
@Component
public class SettleTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(SettleTimeoutJob.class);

    private final WaterSessionRepository sessionRepo;
    private final SettleWaterSessionUseCase settleUseCase;
    private final IotGatewayClient iotGatewayClient;
    private final long timeoutMinutes;

    public SettleTimeoutJob(WaterSessionRepository sessionRepo, SettleWaterSessionUseCase settleUseCase,
                            IotGatewayClient iotGatewayClient,
                            @Value("${water.session-timeout-minutes:10}") long timeoutMinutes) {
        this.sessionRepo = sessionRepo;
        this.settleUseCase = settleUseCase;
        this.iotGatewayClient = iotGatewayClient;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Scheduled(fixedDelay = 30000)
    public void run() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<WaterSession> timedOut = sessionRepo.findTimedOut(threshold);
        if (timedOut.isEmpty()) {
            return;
        }
        log.info("[TIMEOUT] 扫描到 {} 个超时会话", timedOut.size());
        for (WaterSession s : timedOut) {
            try {
                // V1 stub：直接按时间超时，不查 telemetry 最近样本
                try {
                    iotGatewayClient.stop(s.getSn(), s.getId());
                } catch (Exception e) {
                    log.warn("[TIMEOUT] stop 下发失败（继续结算）sessionId={} sn={}: {}",
                            s.getId(), s.getSn(), e.getMessage());
                }
                settleUseCase.settle(s.getId(), s.getTotalLitersMilli(), EndReason.TIMEOUT);
            } catch (Exception e) {
                log.error("[TIMEOUT] 结算超时会话失败 sessionId={}", s.getId(), e);
            }
        }
    }
}

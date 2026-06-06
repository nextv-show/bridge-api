package com.sanshuiyuan.water.session.api;

import com.sanshuiyuan.water.common.ApiResponse;
import com.sanshuiyuan.water.common.BizException;
import com.sanshuiyuan.water.common.ErrorCode;
import com.sanshuiyuan.water.common.H5UserResolver;
import com.sanshuiyuan.water.device.infra.IotGatewayClient;
import com.sanshuiyuan.water.session.application.SettleWaterSessionUseCase;
import com.sanshuiyuan.water.session.application.StartWaterSessionUseCase;
import com.sanshuiyuan.water.session.domain.EndReason;
import com.sanshuiyuan.water.session.domain.WaterSession;
import com.sanshuiyuan.water.session.infra.WaterSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 取水会话 API。C 端 /api/w/water/sessions（H5JwtFilter）；内部 /internal/water/sessions（S2sTokenFilter，幂等）。
 */
@RestController
public class WaterSessionController {

    private static final Logger log = LoggerFactory.getLogger(WaterSessionController.class);

    private final StartWaterSessionUseCase startUseCase;
    private final SettleWaterSessionUseCase settleUseCase;
    private final WaterSessionRepository sessionRepo;
    private final IotGatewayClient iotGatewayClient;
    private final H5UserResolver userResolver;
    private final WaterRateLimiter rateLimiter;

    public WaterSessionController(StartWaterSessionUseCase startUseCase, SettleWaterSessionUseCase settleUseCase,
                                 WaterSessionRepository sessionRepo, IotGatewayClient iotGatewayClient,
                                 H5UserResolver userResolver, WaterRateLimiter rateLimiter) {
        this.startUseCase = startUseCase;
        this.settleUseCase = settleUseCase;
        this.sessionRepo = sessionRepo;
        this.iotGatewayClient = iotGatewayClient;
        this.userResolver = userResolver;
        this.rateLimiter = rateLimiter;
    }

    /** 开启取水（30/min/user）。 */
    @PostMapping("/api/w/water/sessions")
    public Map<String, Object> start(Principal principal, @RequestBody StartRequest req) {
        if (req == null || req.sn() == null || req.sn().isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "缺少设备 SN");
        }
        Long userId = userResolver.resolveUserId(principal.getName());
        rateLimiter.checkStart(userId);
        StartWaterSessionUseCase.StartResult r = startUseCase.start(principal.getName(), req.sn());
        return ApiResponse.ok(Map.of(
                "sessionId", r.sessionId(),
                "maxLitersMilli", r.maxLitersMilli(),
                "pricePerLiterCents", r.pricePerLiterCents()));
    }

    /** 用户主动停止：下发 stop 命令 + 结算。 */
    @PostMapping("/api/w/water/sessions/{id}/stop")
    public Map<String, Object> stop(Principal principal, @PathVariable Long id) {
        Long userId = userResolver.resolveUserId(principal.getName());
        WaterSession session = sessionRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_NOT_FOUND));
        try {
            iotGatewayClient.stop(session.getSn(), id);
        } catch (Exception e) {
            log.warn("停止命令下发失败（继续结算）sessionId={} sn={}: {}", id, session.getSn(), e.getMessage());
        }
        SettleWaterSessionUseCase.SettleResult r =
                settleUseCase.settle(id, session.getTotalLitersMilli(), EndReason.USER_STOP);
        return ApiResponse.ok(Map.of(
                "sessionId", id,
                "billId", r.billId() == null ? "" : r.billId(),
                "litersMilli", r.litersMilli(),
                "amountCents", r.amountCents()));
    }

    /** 查会话状态。 */
    @GetMapping("/api/w/water/sessions/{id}")
    public Map<String, Object> get(Principal principal, @PathVariable Long id) {
        Long userId = userResolver.resolveUserId(principal.getName());
        WaterSession s = sessionRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_NOT_FOUND));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", s.getId());
        data.put("sn", s.getSn());
        data.put("status", s.getStatus().name());
        data.put("startedAt", s.getStartedAt());
        data.put("endedAt", s.getEndedAt());
        data.put("totalLitersMilli", s.getTotalLitersMilli());
        data.put("totalAmountCents", s.getTotalAmountCents());
        data.put("pricePerLiterCents", s.getPricePerLiterCents());
        data.put("endReason", s.getEndReason() == null ? null : s.getEndReason().name());
        return ApiResponse.ok(data);
    }

    /** 内部 S2S 结算（iot-gateway stop 事件调用，幂等）。 */
    @PostMapping("/internal/water/sessions/{id}/settle")
    public Map<String, Object> settle(@PathVariable Long id, @RequestBody SettleRequest req) {
        EndReason reason = parseReason(req.reason());
        long liters = req.litersMilli() == null ? 0L : req.litersMilli();
        SettleWaterSessionUseCase.SettleResult r = settleUseCase.settle(id, liters, reason);
        return ApiResponse.ok(Map.of(
                "sessionId", r.sessionId(),
                "billId", r.billId() == null ? "" : r.billId(),
                "amountCents", r.amountCents(),
                "alreadySettled", r.alreadySettled()));
    }

    private EndReason parseReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return EndReason.USER_STOP;
        }
        try {
            return EndReason.valueOf(reason);
        } catch (IllegalArgumentException e) {
            return EndReason.ERROR;
        }
    }

    public record StartRequest(String sn) {}

    public record SettleRequest(Long litersMilli, String reason) {}
}

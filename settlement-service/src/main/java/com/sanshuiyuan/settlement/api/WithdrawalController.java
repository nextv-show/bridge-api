package com.sanshuiyuan.settlement.api;

import com.sanshuiyuan.settlement.application.CreateWithdrawalUseCase;
import com.sanshuiyuan.settlement.application.payout.PayoutInitiationService;
import com.sanshuiyuan.settlement.auth.SettlementSubjectResolver;
import com.sanshuiyuan.settlement.application.guard.BelowMinimumException;
import com.sanshuiyuan.settlement.application.guard.DailyCountExceededException;
import com.sanshuiyuan.settlement.application.guard.DailyLimitExceededException;
import com.sanshuiyuan.settlement.application.guard.KycNotVerifiedException;
import com.sanshuiyuan.settlement.application.guard.SingleLimitExceededException;
import com.sanshuiyuan.settlement.domain.OwnerWallet;
import com.sanshuiyuan.settlement.domain.WithdrawalOrder;
import com.sanshuiyuan.settlement.domain.WithdrawalPolicy;
import com.sanshuiyuan.settlement.domain.WithdrawalSplit;
import com.sanshuiyuan.settlement.infra.repository.OwnerWalletRepository;
import com.sanshuiyuan.settlement.infra.repository.WithdrawalOrderRepository;
import com.sanshuiyuan.settlement.infra.repository.WithdrawalPolicyRepository;
import com.sanshuiyuan.settlement.infra.repository.WithdrawalSplitRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 钱包查询 + 提现申请 API。JWT subject = user_id。 */
@RestController
@RequestMapping("/api/s")
public class WithdrawalController {

    private final OwnerWalletRepository walletRepository;
    private final WithdrawalOrderRepository orderRepository;
    private final WithdrawalSplitRepository splitRepository;
    private final WithdrawalPolicyRepository policyRepository;
    private final CreateWithdrawalUseCase createWithdrawalUseCase;
    private final PayoutInitiationService payoutInitiationService;
    private final SettlementSubjectResolver subjectResolver;
    private final String mchId;
    private final String mpAppId;

    public WithdrawalController(OwnerWalletRepository walletRepository,
                                WithdrawalOrderRepository orderRepository,
                                WithdrawalSplitRepository splitRepository,
                                WithdrawalPolicyRepository policyRepository,
                                CreateWithdrawalUseCase createWithdrawalUseCase,
                                PayoutInitiationService payoutInitiationService,
                                SettlementSubjectResolver subjectResolver,
                                @org.springframework.beans.factory.annotation.Value("${wxpay.mch-id:stub}") String mchId,
                                @org.springframework.beans.factory.annotation.Value("${wxpay.mp-app-id:stub}") String mpAppId) {
        this.walletRepository = walletRepository;
        this.orderRepository = orderRepository;
        this.splitRepository = splitRepository;
        this.policyRepository = policyRepository;
        this.createWithdrawalUseCase = createWithdrawalUseCase;
        this.payoutInitiationService = payoutInitiationService;
        this.subjectResolver = subjectResolver;
        this.mchId = mchId;
        this.mpAppId = mpAppId;
    }

    @GetMapping("/owner/wallet")
    public ResponseEntity<Map<String, Object>> getWallet(Authentication auth) {
        Long userId = subjectResolver.resolveUserId(auth.getName());
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "UNAUTHORIZED"));
        }
        OwnerWallet wallet = walletRepository.findById(userId)
                .orElseGet(() -> new OwnerWallet(userId, 0L, 0L));
        return ResponseEntity.ok(Map.of("code", 0, "data", Map.of(
                "balance_cents", wallet.getBalanceCents(),
                "frozen_cents", wallet.getFrozenCents(),
                "currency", "CNY"
        )));
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<Map<String, Object>> createWithdrawal(@RequestBody Map<String, Object> body,
                                                                Authentication auth) {
        Long userId = subjectResolver.resolveUserId(auth.getName());
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "UNAUTHORIZED"));
        }
        Long amountCents = Long.valueOf(body.get("amount_cents").toString());
        String clientRequestId = (String) body.getOrDefault("client_request_id", UUID.randomUUID().toString());
        boolean dryRun = Boolean.TRUE.equals(body.get("dry_run"));

        try {
            WithdrawalOrder order = createWithdrawalUseCase.create(userId, amountCents, clientRequestId, dryRun);

            Map<String, Object> result = new LinkedHashMap<>();
            if (dryRun) {
                result.put("preview", Map.of(
                        "gross", order.getGrossCents(),
                        "fee", order.getFeeCents(),
                        "net", order.getCashCents(),
                        "cash_part", order.getCashCents()
                ));
            } else {
                // 发起商家转账（transfer-bills）→ 返回 package_info 供小程序 wx.requestMerchantTransfer 确认。
                PayoutInitiationService.InitiationResult init = payoutInitiationService.initiate(order.getId());
                if (!init.ok()) {
                    // 已退款（订单 FAILED），返回业务失败。
                    return ResponseEntity.status(502).body(Map.of(
                            "code", 502, "message", "PAYOUT_INITIATE_FAILED:" + init.errorCode()));
                }
                result.put("withdrawal_id", order.getId());
                result.put("status", "PROCESSING");
                result.put("package_info", init.packageInfo());
                result.put("transfer_bill_no", init.transferBillNo());
                result.put("mch_id", mchId);
                result.put("app_id", mpAppId);
            }
            return ResponseEntity.ok(Map.of("code", 0, "data", result));
        } catch (KycNotVerifiedException e) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "KYC_NOT_VERIFIED"));
        } catch (SingleLimitExceededException e) {
            return ResponseEntity.status(429).body(Map.of("code", 429, "message", "SINGLE_LIMIT_EXCEEDED"));
        } catch (DailyLimitExceededException e) {
            return ResponseEntity.status(429).body(Map.of("code", 429, "message", "DAILY_LIMIT_EXCEEDED"));
        } catch (DailyCountExceededException e) {
            return ResponseEntity.status(429).body(Map.of("code", 429, "message", "DAILY_COUNT_EXCEEDED"));
        } catch (BelowMinimumException e) {
            return ResponseEntity.status(422).body(Map.of("code", 422, "message", "BELOW_MINIMUM"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(422).body(Map.of("code", 422, "message", e.getMessage()));
        }
    }

    @GetMapping("/withdrawals/policy")
    public ResponseEntity<Map<String, Object>> getPolicy(Authentication auth) {
        Long userId = subjectResolver.resolveUserId(auth.getName());
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "UNAUTHORIZED"));
        }
        WithdrawalPolicy policy = policyRepository.findTopByOrderByEffectiveFromDesc()
                .orElseThrow(() -> new IllegalStateException("No withdrawal policy configured"));
        return ResponseEntity.ok(Map.of("code", 0, "data", Map.of(
                "fee_bp", policy.getFeeBp(),
                "fee_rate_display", String.format("%d%%", policy.getFeeBp() / 100),
                "single_max_cents", policy.getSingleMaxCents(),
                "daily_max_cents", policy.getDailyMaxCents(),
                "daily_max_count", policy.getDailyMaxCount(),
                "min_cents", policy.getMinCents(),
                "currency", "CNY"
        )));
    }

    @GetMapping("/withdrawals/{id}")
    public ResponseEntity<Map<String, Object>> getWithdrawal(@PathVariable Long id, Authentication auth) {
        Long userId = subjectResolver.resolveUserId(auth.getName());
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "UNAUTHORIZED"));
        }
        WithdrawalOrder order = orderRepository.findById(id).orElse(null);
        if (order == null || !order.getUserId().equals(userId)) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "Not found"));
        }
        List<WithdrawalSplit> splits = splitRepository.findByOrderId(id);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", order.getId());
        data.put("user_id", order.getUserId());
        data.put("gross_cents", order.getGrossCents());
        data.put("fee_cents", order.getFeeCents());
        data.put("cash_cents", order.getCashCents());
        data.put("status", order.getStatus().name());
        data.put("failure_reason", order.getFailureReason());
        data.put("created_at", order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
        data.put("completed_at", order.getCompletedAt() != null ? order.getCompletedAt().toString() : null);

        List<Map<String, Object>> splitList = splits.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", s.getKind().name());
            m.put("amount_cents", s.getAmountCents());
            m.put("channel", s.getChannel().name());
            m.put("status", s.getStatus().name());
            m.put("failure_reason", s.getFailureReason());
            return m;
        }).toList();
        data.put("splits", splitList);

        return ResponseEntity.ok(Map.of("code", 0, "data", data));
    }

    @GetMapping("/withdrawals/mine")
    public ResponseEntity<Map<String, Object>> getMyWithdrawals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        Long userId = subjectResolver.resolveUserId(auth.getName());
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "UNAUTHORIZED"));
        }
        List<WithdrawalOrder> orders = orderRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, limit));

        List<Map<String, Object>> items = orders.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("gross_cents", o.getGrossCents());
            m.put("fee_cents", o.getFeeCents());
            m.put("cash_cents", o.getCashCents());
            m.put("status", o.getStatus().name());
            m.put("created_at", o.getCreatedAt() != null ? o.getCreatedAt().toString() : null);
            return m;
        }).toList();

        return ResponseEntity.ok(Map.of("code", 0, "data", items));
    }
}

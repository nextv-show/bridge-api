package com.sanshuiyuan.settlement.api;

import com.sanshuiyuan.settlement.application.CreateWithdrawalUseCase;
import com.sanshuiyuan.settlement.application.guard.DailyLimitExceededException;
import com.sanshuiyuan.settlement.application.guard.KycNotVerifiedException;
import com.sanshuiyuan.settlement.application.guard.SingleLimitExceededException;
import com.sanshuiyuan.settlement.domain.OwnerWallet;
import com.sanshuiyuan.settlement.domain.WithdrawalOrder;
import com.sanshuiyuan.settlement.domain.WithdrawalSplit;
import com.sanshuiyuan.settlement.infra.repository.OwnerWalletRepository;
import com.sanshuiyuan.settlement.infra.repository.WithdrawalOrderRepository;
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
    private final CreateWithdrawalUseCase createWithdrawalUseCase;

    public WithdrawalController(OwnerWalletRepository walletRepository,
                                WithdrawalOrderRepository orderRepository,
                                WithdrawalSplitRepository splitRepository,
                                CreateWithdrawalUseCase createWithdrawalUseCase) {
        this.walletRepository = walletRepository;
        this.orderRepository = orderRepository;
        this.splitRepository = splitRepository;
        this.createWithdrawalUseCase = createWithdrawalUseCase;
    }

    @GetMapping("/owner/wallet")
    public ResponseEntity<Map<String, Object>> getWallet(Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
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
        Long userId = Long.parseLong(auth.getName());
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
                result.put("withdrawal_id", order.getId());
                result.put("status", order.getStatus().name());
            }
            return ResponseEntity.ok(Map.of("code", 0, "data", result));
        } catch (KycNotVerifiedException e) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "KYC_NOT_VERIFIED"));
        } catch (SingleLimitExceededException e) {
            return ResponseEntity.status(429).body(Map.of("code", 429, "message", "SINGLE_LIMIT_EXCEEDED"));
        } catch (DailyLimitExceededException e) {
            return ResponseEntity.status(429).body(Map.of("code", 429, "message", "DAILY_LIMIT_EXCEEDED"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(422).body(Map.of("code", 422, "message", e.getMessage()));
        }
    }

    @GetMapping("/withdrawals/{id}")
    public ResponseEntity<Map<String, Object>> getWithdrawal(@PathVariable Long id, Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
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
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        List<WithdrawalOrder> orders = orderRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));

        List<Map<String, Object>> items = orders.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("gross_cents", o.getGrossCents());
            m.put("cash_cents", o.getCashCents());
            m.put("status", o.getStatus().name());
            m.put("created_at", o.getCreatedAt() != null ? o.getCreatedAt().toString() : null);
            return m;
        }).toList();

        return ResponseEntity.ok(Map.of("code", 0, "data", items));
    }
}

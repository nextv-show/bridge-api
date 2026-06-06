package com.sanshuiyuan.water.session.api;

import com.sanshuiyuan.water.common.ApiResponse;
import com.sanshuiyuan.water.common.BizException;
import com.sanshuiyuan.water.common.ErrorCode;
import com.sanshuiyuan.water.common.H5UserResolver;
import com.sanshuiyuan.water.session.domain.WaterBill;
import com.sanshuiyuan.water.session.infra.WaterBillRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * C 端账单 API（/api/w/water/bills，H5JwtFilter）。
 */
@RestController
@RequestMapping("/api/w/water/bills")
public class WaterBillController {

    private final WaterBillRepository billRepo;
    private final H5UserResolver userResolver;
    private final String explorerUrlTemplate;

    public WaterBillController(WaterBillRepository billRepo, H5UserResolver userResolver,
                               @Value("${antchain.explorer.url-template:}") String explorerUrlTemplate) {
        this.billRepo = billRepo;
        this.userResolver = userResolver;
        this.explorerUrlTemplate = explorerUrlTemplate;
    }

    /** 我的账单列表。 */
    @GetMapping("/mine")
    public Map<String, Object> mine(Principal principal) {
        Long userId = userResolver.resolveUserId(principal.getName());
        List<Map<String, Object>> bills = billRepo.findByUserIdOrderBySettledAtDesc(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ApiResponse.ok(bills);
    }

    /** 账单详情（仅本人可见）。 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(Principal principal, @PathVariable Long id) {
        Long userId = userResolver.resolveUserId(principal.getName());
        WaterBill bill = billRepo.findById(id)
                .filter(b -> b.getUserId().equals(userId))
                .orElseThrow(() -> new BizException(ErrorCode.BILL_NOT_FOUND));
        return ApiResponse.ok(toDto(bill));
    }

    /** 账单存证详情（链上状态 + 交易哈希 + 浏览器链接，仅本人可见）。 */
    @GetMapping("/{id}/evidence")
    public Map<String, Object> evidence(Principal principal, @PathVariable Long id) {
        Long userId = userResolver.resolveUserId(principal.getName());
        WaterBill bill = billRepo.findById(id)
                .filter(b -> b.getUserId().equals(userId))
                .orElseThrow(() -> new BizException(ErrorCode.BILL_NOT_FOUND));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chainStatus", bill.getChainStatus().name());
        result.put("chainTxHash", bill.getChainTxHash());
        result.put("explorerUrl", bill.getChainTxHash() == null ? null : explorerUrlTemplate + bill.getChainTxHash());
        return ApiResponse.ok(result);
    }

    private Map<String, Object> toDto(WaterBill b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("sessionId", b.getSessionId());
        m.put("sn", b.getSn());
        m.put("litersMilli", b.getLitersMilli());
        m.put("pricePerLiterCents", b.getPricePerLiterCents());
        m.put("amountCents", b.getAmountCents());
        m.put("settledAt", b.getSettledAt());
        m.put("chainStatus", b.getChainStatus().name());
        m.put("chainTxHash", b.getChainTxHash());
        return m;
    }
}

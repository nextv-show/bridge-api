package com.sanshuiyuan.asset.rebate.api;

import com.sanshuiyuan.asset.rebate.api.dto.PendingRebateItem;
import com.sanshuiyuan.asset.rebate.application.RebateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 推荐返利查询接口（小程序 user-JWT，<b>需登录态</b>）。
 *
 * <p><b>合规铁律：</b>FROZEN（冷静期中）不下发具体金额，仅状态 + 触发订单引用；仅 CONFIRMED 才返金额。
 * 金额可见性收敛在 {@link PendingRebateItem#from} 统一裁剪。只读受益人本人记录，不暴露关系链层级深度。
 *
 * <p>GET /rebates  —— 当前用户作为受益人的返利列表。
 */
@RestController
@RequestMapping("/rebates")
public class RebateController {

    private final RebateService rebateService;

    public RebateController(RebateService rebateService) {
        this.rebateService = rebateService;
    }

    @GetMapping
    public ResponseEntity<List<PendingRebateItem>> myRebates(@AuthenticationPrincipal Long userId) {
        List<PendingRebateItem> items = rebateService.listForBeneficiary(userId).stream()
                .map(PendingRebateItem::from)
                .toList();
        return ResponseEntity.ok(items);
    }
}

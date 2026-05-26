package com.sanshuiyuan.h5.rebate.api;

import com.sanshuiyuan.h5.auth.CurrentOpenid;
import com.sanshuiyuan.h5.common.ApiResponse;
import com.sanshuiyuan.h5.rebate.api.dto.PendingRebateItem;
import com.sanshuiyuan.h5.rebate.api.dto.RebateSummary;
import com.sanshuiyuan.h5.rebate.application.RebateService;
import com.sanshuiyuan.h5.referral.H5User;
import com.sanshuiyuan.h5.referral.H5UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * 冷静期返利查询接口（011，<b>需登录态</b>）。
 *
 * <p><b>合规铁律：</b>FROZEN（冷静期中）不下发具体金额，仅状态 + 订单引用；仅 CONFIRMED 才返金额。
 * 金额可见性收敛在 {@link PendingRebateItem#from} 统一裁剪。只读受益人本人记录，不暴露关系链层级。
 */
@RestController
@RequestMapping("/api/h5/rebates")
@Tag(name = "Rebate", description = "冷静期返利（登录态）")
public class RebateController {

    private final RebateService rebateService;
    private final H5UserRepository userRepo;

    public RebateController(RebateService rebateService, H5UserRepository userRepo) {
        this.rebateService = rebateService;
        this.userRepo = userRepo;
    }

    @Operation(summary = "我的返利列表",
            description = "当前用户作为受益人的返利记录。冷静期中（FROZEN）不返回金额，仅已确认（CONFIRMED）返金额。")
    @GetMapping("/pending")
    public ApiResponse<List<PendingRebateItem>> pending() {
        return ApiResponse.ok(currentBeneficiaryId()
                .map(id -> rebateService.listForBeneficiary(id).stream()
                        .map(PendingRebateItem::from)
                        .toList())
                .orElseGet(List::of));
    }

    @Operation(summary = "我的返利摘要",
            description = "已确认总额（仅 CONFIRMED 计入）、冻结中笔数、已取消笔数。冷静期中金额不计入总额。")
    @GetMapping("/summary")
    public ApiResponse<RebateSummary> summary() {
        return ApiResponse.ok(currentBeneficiaryId()
                .map(rebateService::summarize)
                .orElseGet(RebateSummary::empty));
    }

    /** 解析当前登录用户的受益人 id（= 其 H5 user_id）。未注册 H5 身份则视为无返利。 */
    private Optional<Long> currentBeneficiaryId() {
        String openid = CurrentOpenid.require();
        return userRepo.findByOpenid(openid).map(H5User::getId);
    }
}

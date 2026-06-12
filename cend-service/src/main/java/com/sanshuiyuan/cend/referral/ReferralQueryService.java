package com.sanshuiyuan.cend.referral;

import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.OrderStatus;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import com.sanshuiyuan.cend.referral.api.MyReferralsResponse;
import com.sanshuiyuan.cend.referral.api.ReferralItemResponse;
import com.sanshuiyuan.cend.referral.api.ReferralSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 我的推荐查询服务（015 T15.2）。
 *
 * <p><b>合规铁律</b>：
 * <ul>
 *   <li>仅做 <b>L1 单层正向展开</b>（{@code findByInviterId}）查「我直接推荐的人」，绝不向上 / 向下递归；</li>
 *   <li>是否已购买仅以被推荐人<b>自身 openid</b> 的已支付订单判定，不触及订单的 inviter_id / grand_inviter_id 快照；</li>
 *   <li>返回 DTO <b>零层级字段</b>：不暴露 inviter_id / grand_inviter_id / level / L1 / L2。</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ReferralQueryService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CendUserRepository userRepo;
    private final CendOrderRepository orderRepo;
    private final ReferralDisplayNameResolver displayNameResolver;

    public ReferralQueryService(CendUserRepository userRepo, CendOrderRepository orderRepo,
                                ReferralDisplayNameResolver displayNameResolver) {
        this.userRepo = userRepo;
        this.orderRepo = orderRepo;
        this.displayNameResolver = displayNameResolver;
    }

    /**
     * 查询当前用户（按 openid 定位）的直接推荐人列表与汇总。
     *
     * @param openid 当前登录用户 openid
     * @param status 过滤条件：{@code ALL}（默认）/ {@code REGISTERED} / {@code PURCHASED}；非白名单值按 ALL 处理
     */
    public MyReferralsResponse myReferrals(String openid, String status) {
        String filter = normalizeStatus(status);
        CendUser me = userRepo.findByOpenid(openid)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        // L1 单层正向展开：仅查我直接推荐的人。
        List<CendUser> referred = userRepo.findByInviterId(me.getId());

        // 批量取被推荐人各自最近一笔已支付订单的支付时间（按 openid 单点匹配）。
        Map<String, LocalDateTime> latestPaidByOpenid = latestPaidAt(referred);

        // 批量解析展示名：微信昵称脱敏 > 实名脱敏 > 手机尾号（单次 KYC 查询，避免 N+1）。
        Map<String, String> nicknameByOpenid = new HashMap<>();
        for (CendUser u : referred) {
            nicknameByOpenid.put(u.getOpenid(), u.getNickname());
        }
        Map<String, String> displayNameByOpenid = displayNameResolver.resolveBatch(
                nicknameByOpenid.keySet(), nicknameByOpenid);

        int registeredCount = 0;
        int purchasedCount = 0;
        List<ReferralItemResponse> items = new ArrayList<>();
        for (CendUser u : referred) {
            LocalDateTime paidAt = latestPaidByOpenid.get(u.getOpenid());
            boolean purchased = paidAt != null;
            if (purchased) {
                purchasedCount++;
            } else {
                registeredCount++;
            }
            // 过滤：REGISTERED 只留未购买，PURCHASED 只留已购买，ALL 全留。
            if ("REGISTERED".equals(filter) && purchased) {
                continue;
            }
            if ("PURCHASED".equals(filter) && !purchased) {
                continue;
            }
            items.add(new ReferralItemResponse(
                    u.getId(),
                    NicknameMasker.mask(u.getNickname()),
                    displayNameByOpenid.getOrDefault(u.getOpenid(), ""),
                    u.getAvatarUrl(),
                    formatDate(u.getCreatedAt()),
                    purchased ? "PURCHASED" : "REGISTERED",
                    purchased ? formatDate(paidAt) : null));
        }

        // 按注册日期倒序（新推荐在前），同日以 userId 稳定排序。
        items.sort(Comparator
                .comparing(ReferralItemResponse::registeredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ReferralItemResponse::userId));

        ReferralSummaryResponse summary =
                new ReferralSummaryResponse(referred.size(), registeredCount, purchasedCount);
        return new MyReferralsResponse(summary, items);
    }

    /** openid → 最近一笔已支付（PAID）订单的支付时间；被推荐人列表为空时不发起查询。 */
    private Map<String, LocalDateTime> latestPaidAt(List<CendUser> referred) {
        if (referred.isEmpty()) {
            return Map.of();
        }
        List<String> openids = referred.stream().map(CendUser::getOpenid).toList();
        List<CendOrder> paidOrders = orderRepo.findByOpenidInAndStatus(openids, OrderStatus.PAID);
        Map<String, LocalDateTime> latest = new HashMap<>();
        for (CendOrder o : paidOrders) {
            LocalDateTime paidAt = o.getPaidAt();
            if (paidAt == null) {
                continue;
            }
            latest.merge(o.getOpenid(), paidAt, (a, b) -> a.isAfter(b) ? a : b);
        }
        return latest;
    }

    /** status 白名单校验：仅接受 ALL / REGISTERED / PURCHASED（大小写不敏感），其余按 ALL 处理。 */
    private String normalizeStatus(String status) {
        if (status == null) {
            return "ALL";
        }
        String s = status.trim().toUpperCase();
        return switch (s) {
            case "REGISTERED", "PURCHASED", "ALL" -> s;
            default -> "ALL";
        };
    }

    private static String formatDate(LocalDateTime dt) {
        return dt == null ? null : dt.format(DATE);
    }
}

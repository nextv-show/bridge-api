package com.sanshuiyuan.user.referral;

import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import com.sanshuiyuan.user.referral.api.MyReferralsResponse;
import com.sanshuiyuan.user.referral.api.ReferralItemResponse;
import com.sanshuiyuan.user.referral.api.ReferralSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 我的推荐查询服务（015 T15.2，自 h5-service 忠实移植）。
 *
 * <p><b>合规铁律</b>：
 * <ul>
 *   <li>仅做 <b>L1 单层正向展开</b>（{@code findByInviterId}）查「我直接推荐的人」，绝不向上 / 向下递归；</li>
 *   <li>返回 DTO <b>零层级字段</b>：不暴露 inviter_id / grand_inviter_id / level / L1 / L2。</li>
 * </ul>
 *
 * <p><b>购买状态待补全</b>：购机订单在 asset-service（另一个库），user-service 查不到购买状态。
 * 本期 {@code purchased} 一律按 false / {@code REGISTERED} 处理；后续经 asset-service 内部接口补全。
 */
@Service
@Transactional(readOnly = true)
public class ReferralQueryService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserRepository userRepo;

    public ReferralQueryService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    /**
     * 查询当前用户（按 userId 定位）的直接推荐人列表与汇总。
     *
     * @param currentUserId 当前登录用户 user_id
     * @param status        过滤条件：{@code ALL}（默认）/ {@code REGISTERED} / {@code PURCHASED}；非白名单值按 ALL 处理
     */
    public MyReferralsResponse myReferrals(Long currentUserId, String status) {
        String filter = normalizeStatus(status);

        // L1 单层正向展开：仅查我直接推荐的人。
        List<User> referred = userRepo.findByInviterId(currentUserId);

        int registeredCount = 0;
        int purchasedCount = 0;
        List<ReferralItemResponse> items = new ArrayList<>();
        for (User u : referred) {
            // 购买状态待经 asset-service 内部接口补全（后续）；本期一律按 false / REGISTERED 处理。
            boolean purchased = false;
            LocalDateTime paidAt = null;
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

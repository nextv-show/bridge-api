package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.identity.MatchingUserResolver;
import com.sanshuiyuan.matching.request.api.ApiException;
import com.sanshuiyuan.matching.request.api.dto.RequestItem;
import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import com.sanshuiyuan.matching.request.domain.RequestStatus;
import com.sanshuiyuan.matching.request.infra.MatchingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** FR-2：我的需求列表 + 详情。本人看自己手机号明文。 */
@Service
public class MyRequestsQueryService {

    private final MatchingRequestRepository repo;
    private final MatchingUserResolver userResolver;
    private final RequestItemMapper mapper;

    public MyRequestsQueryService(MatchingRequestRepository repo,
                                  MatchingUserResolver userResolver,
                                  RequestItemMapper mapper) {
        this.repo = repo;
        this.userResolver = userResolver;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<RequestItem> mine(String subject, String statusFilter) {
        // 只读解析，不创建 users 行（避免 readOnly 事务内写库 + 浏览污染表）。
        // 没有 users 行的用户不可能发过需求 → 空列表。
        var uid = userResolver.findUserId(subject);
        if (uid.isEmpty()) {
            return List.of();
        }
        long userId = uid.get();
        RequestStatus status = parseStatus(statusFilter);
        List<MatchingRequest> rows = (status == null)
                ? repo.findByUserIdOrderByCreatedAtDesc(userId)
                : repo.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        // 本人看自己 → 明文，distance_km=null。
        return rows.stream().map(r -> mapper.toItem(r, true, null)).toList();
    }

    /**
     * 详情：调用方是发起人或 locked_by 时手机号明文，否则脱敏。
     */
    @Transactional(readOnly = true)
    public RequestItem detail(String subject, long requestId) {
        // 只读解析；无 users 行（-1 哨兵，不匹配任何真实 id）→ 必然脱敏。
        long userId = userResolver.findUserId(subject).orElse(-1L);
        MatchingRequest r = repo.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "需求不存在"));
        boolean plain = userId == r.getUserId()
                || (r.getLockedByUserId() != null && userId == r.getLockedByUserId());
        // 传 viewerUserId 以计算 is_owner / is_lock_owner，供详情页取消/释放按钮判定。
        return mapper.toItem(r, plain, null, userId);
    }

    private RequestStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return RequestStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.unprocessable("INVALID_STATUS",
                    "status 必须是 OPEN/LOCKED/FULFILLED/CANCELLED/EXPIRED 之一");
        }
    }
}

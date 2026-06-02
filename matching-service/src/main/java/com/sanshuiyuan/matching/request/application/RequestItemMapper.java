package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.crypto.IdCardCipher;
import com.sanshuiyuan.matching.crypto.PhoneMasking;
import com.sanshuiyuan.matching.request.api.dto.RequestItem;
import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import org.springframework.stereotype.Component;

/** MatchingRequest → RequestItem，按调用方权限决定手机号明文/脱敏。 */
@Component
public class RequestItemMapper {

    private final IdCardCipher cipher;

    public RequestItemMapper(IdCardCipher cipher) {
        this.cipher = cipher;
    }

    /**
     * @param plainPhone true 返回解密明文，false 返回脱敏（前3后4）
     * @param distanceKm 可空（mine/详情为 null）
     */
    public RequestItem toItem(MatchingRequest r, boolean plainPhone, Double distanceKm) {
        return toItem(r, plainPhone, distanceKm, null);
    }

    /**
     * 带调用方身份的映射：据 {@code viewerUserId} 计算 is_owner / is_lock_owner，供详情页取消/释放按钮判定。
     *
     * @param viewerUserId 调用方 users.id；为空时两标记均 false（mine/nearby 列表无需此判定）
     */
    public RequestItem toItem(MatchingRequest r, boolean plainPhone, Double distanceKm, Long viewerUserId) {
        String plain = cipher.decrypt(r.getContactPhoneEnc());
        String phone = plainPhone ? plain : PhoneMasking.mask(plain);
        boolean isOwner = viewerUserId != null && viewerUserId.equals(r.getUserId());
        boolean isLockOwner = viewerUserId != null
                && r.getLockedByUserId() != null
                && viewerUserId.equals(r.getLockedByUserId());
        return new RequestItem(
                r.getId(),
                r.getContactName(),
                phone,
                r.getAddress(),
                r.getLat(),
                r.getLng(),
                r.getSceneType().name(),
                r.getEstDailyLiters(),
                r.getExpectedPriceTier().name(),
                r.getStatus().name(),
                distanceKm,
                r.getLockedAt(),
                r.getCreatedAt(),
                r.getUserId(),
                r.getLockedByUserId(),
                isOwner,
                isLockOwner);
    }
}

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
        String plain = cipher.decrypt(r.getContactPhoneEnc());
        String phone = plainPhone ? plain : PhoneMasking.mask(plain);
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
                r.getCreatedAt());
    }
}

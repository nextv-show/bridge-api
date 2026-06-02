package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.crypto.IdCardCipher;
import com.sanshuiyuan.matching.crypto.PhoneMasking;
import com.sanshuiyuan.matching.identity.MatchingUserResolver;
import com.sanshuiyuan.matching.request.api.ApiException;
import com.sanshuiyuan.matching.request.api.dto.CreateRequestBody;
import com.sanshuiyuan.matching.request.api.dto.CreateRequestResponse;
import com.sanshuiyuan.matching.request.domain.MatchingRequest;
import com.sanshuiyuan.matching.request.domain.PriceTier;
import com.sanshuiyuan.matching.request.domain.RequestStatus;
import com.sanshuiyuan.matching.request.domain.SceneType;
import com.sanshuiyuan.matching.request.infra.MatchingRequestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** FR-1：发布撮合需求。校验→限频→身份解析→加密落库。 */
@Service
public class CreateRequestUseCase {

    private static final int MAX_OPEN_PER_PHONE_24H = 3;

    private final MatchingRequestRepository repo;
    private final MatchingUserResolver userResolver;
    private final IdCardCipher cipher;
    private final GeoHashIndexer geoHashIndexer;
    private final PhoneRateLimiter phoneRateLimiter;

    @PersistenceContext
    private EntityManager entityManager;

    public CreateRequestUseCase(MatchingRequestRepository repo,
                                MatchingUserResolver userResolver,
                                IdCardCipher cipher,
                                GeoHashIndexer geoHashIndexer,
                                PhoneRateLimiter phoneRateLimiter) {
        this.repo = repo;
        this.userResolver = userResolver;
        this.cipher = cipher;
        this.geoHashIndexer = geoHashIndexer;
        this.phoneRateLimiter = phoneRateLimiter;
    }

    @Transactional
    public CreateRequestResponse create(String subject, CreateRequestBody body) {
        SceneType sceneType = parseScene(body.sceneType());
        PriceTier tier = parseTier(body.expectedPriceTier());

        String normalizedPhone = PhoneMasking.normalize(body.contactPhone());
        if (normalizedPhone.length() != 11) {
            throw ApiException.unprocessable("INVALID_PHONE", "手机号格式不正确");
        }
        String phoneHash = cipher.idCardHash(normalizedPhone);

        // B.2.5 防刷：同手机号 24h 内 status=OPEN ≤3，**原子**（FOR UPDATE 间隙锁，见 repo 方法注释）。
        // 先于限频令牌消费做：被 TOO_MANY_OPEN 拒绝不应白白扣掉手机号 5/日 配额。
        long openCount = repo.lockOpenIdsByPhoneSince(
                phoneHash, RequestStatus.OPEN, LocalDateTime.now().minusHours(24)).size();
        if (openCount >= MAX_OPEN_PER_PHONE_24H) {
            throw ApiException.tooManyRequests("TOO_MANY_OPEN", "该手机号 24 小时内待撮合需求过多");
        }

        // B.2.4 按手机号限频（60s 1 次 + 24h 5 次）。
        if (!phoneRateLimiter.tryConsume(phoneHash)) {
            throw ApiException.tooManyRequests("RATE_LIMITED", "操作过于频繁，请稍后再试");
        }

        long userId = userResolver.resolveUserId(subject);

        MatchingRequest r = new MatchingRequest();
        r.setUserId(userId);
        r.setContactName(body.contactName());
        r.setContactPhoneEnc(cipher.encrypt(normalizedPhone));
        r.setContactPhoneHash(phoneHash);
        r.setAddress(body.address());
        r.setLat(body.lat());
        r.setLng(body.lng());
        r.setGeohash6(geoHashIndexer.geohash6(body.lat(), body.lng()));
        r.setSceneType(sceneType);
        r.setEstDailyLiters(body.estDailyLiters());
        r.setExpectedPriceTier(tier);
        r.setStatus(RequestStatus.OPEN);
        r.setVersion(0);

        MatchingRequest saved = repo.saveAndFlush(r);
        // created_at 由 DB 默认值生成，insertable=false 不回填，refresh 取真实值。
        entityManager.refresh(saved);
        return new CreateRequestResponse(saved.getId(), RequestStatus.OPEN.name(), saved.getCreatedAt());
    }

    private SceneType parseScene(String raw) {
        try {
            return SceneType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.unprocessable("INVALID_SCENE_TYPE",
                    "scene_type 必须是 HOME/OFFICE/SHOP/CAMPUS 之一");
        }
    }

    private PriceTier parseTier(String raw) {
        try {
            return PriceTier.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.unprocessable("INVALID_PRICE_TIER",
                    "expected_price_tier 必须是 T_040/T_080/T_120/T_150 之一");
        }
    }
}

package com.sanshuiyuan.cend.referral;

import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import com.sanshuiyuan.cend.checkout.infra.repository.KycRecordRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 推荐场景「可识别展示名」解析器（修复列表全显示「微信用户」）。
 *
 * <p>微信自 2022 起 {@code wx.login} 不再回传昵称/头像，{@code h5_users.nickname} 多为空，
 * 仅靠微信昵称会大面积退化为前端兜底文案「微信用户」。本解析器在不暴露明文 PII 的前提下，
 * 用同库已脱敏的实名/手机号补位，给出对邀请人可识别的名字。
 *
 * <p><b>兜底优先级</b>：微信昵称脱敏（{@link NicknameMasker}） &gt; 实名脱敏（{@code real_name_mask}，如「张 **」）
 * &gt; 手机尾号（{@code phone_mask}，如「138****8888」） &gt; 空串（交由前端显示「微信用户」）。
 *
 * <p><b>合规</b>：仅读取 PASS 实名记录的<b>既有脱敏字段</b>，绝不解密、不返回明文，不触及关系链层级。
 */
@Component
public class ReferralDisplayNameResolver {

    private final KycRecordRepository kycRepo;

    public ReferralDisplayNameResolver(KycRecordRepository kycRepo) {
        this.kycRepo = kycRepo;
    }

    /** 单个 openid 的展示名解析；昵称优先，缺失时回落到最近一条 PASS 实名记录的脱敏字段。 */
    public String resolve(String openid, String nickname) {
        String nick = NicknameMasker.mask(nickname);
        if (!nick.isBlank()) {
            return nick;
        }
        return kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc(openid, KycStatus.PASS)
                .map(ReferralDisplayNameResolver::fromKyc)
                .orElse("");
    }

    /**
     * 批量解析一组 openid 的展示名，单次 KYC 查询避免 N+1。
     *
     * @return openid → 展示名（可能为空串，表示无任何可识别信息）
     */
    public Map<String, String> resolveBatch(Collection<String> openids, Map<String, String> nicknameByOpenid) {
        Map<String, String> result = new HashMap<>();
        if (openids.isEmpty()) {
            return result;
        }
        // 同一 openid 可能有多条 PASS（历史作废重认证），取 verified_at 最新一条的脱敏信息。
        Map<String, KycRecord> latestKyc = new HashMap<>();
        for (KycRecord k : kycRepo.findAllByOpenidInAndStatus(openids, KycStatus.PASS)) {
            latestKyc.merge(k.getOpenid(), k, (a, b) -> isAfter(verifiedAt(b), verifiedAt(a)) ? b : a);
        }
        for (String openid : openids) {
            String nick = NicknameMasker.mask(nicknameByOpenid.get(openid));
            if (!nick.isBlank()) {
                result.put(openid, nick);
                continue;
            }
            KycRecord k = latestKyc.get(openid);
            result.put(openid, k == null ? "" : fromKyc(k));
        }
        return result;
    }

    private static String fromKyc(KycRecord k) {
        if (k.getRealNameMask() != null && !k.getRealNameMask().isBlank()) {
            return k.getRealNameMask();
        }
        if (k.getPhoneMask() != null && !k.getPhoneMask().isBlank()) {
            return k.getPhoneMask();
        }
        return "";
    }

    private static LocalDateTime verifiedAt(KycRecord k) {
        return k.getVerifiedAt();
    }

    private static boolean isAfter(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return false;
        }
        if (b == null) {
            return true;
        }
        return a.isAfter(b);
    }
}

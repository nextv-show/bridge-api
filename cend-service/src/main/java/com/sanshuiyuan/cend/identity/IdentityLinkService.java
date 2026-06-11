package com.sanshuiyuan.cend.identity;

import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import com.sanshuiyuan.cend.checkout.infra.crypto.IdCardCipher;
import com.sanshuiyuan.cend.checkout.infra.repository.KycRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 微信手机号核验跨端关联：用户在小程序一键拿到微信级已验证手机号，匹配到同一手机号已实名(PASS)的自然人，
 * 为本端 openid 建立"仅可见"关联（{@link IdentityLink}），从而在"我的订单"看到其在另一端（公众号）的历史订单。
 *
 * <p><b>安全</b>：仅按"微信已验证手机号 == 已实名记录手机号哈希"匹配；不创建 KYC PASS、不授认购资格（资金路径仍严格）。
 * 手机号属敏感信息，全程不入日志（仅记 openid 与是否命中）。
 */
@Service
public class IdentityLinkService {

    private static final Logger log = LoggerFactory.getLogger(IdentityLinkService.class);

    private final WxMiniPhoneClient phoneClient;
    private final IdCardCipher cipher;
    private final KycRecordRepository kycRepo;
    private final IdentityLinkRepository linkRepo;

    public IdentityLinkService(WxMiniPhoneClient phoneClient, IdCardCipher cipher,
                               KycRecordRepository kycRepo, IdentityLinkRepository linkRepo) {
        this.phoneClient = phoneClient;
        this.cipher = cipher;
        this.kycRepo = kycRepo;
        this.linkRepo = linkRepo;
    }

    public record LinkResult(boolean linked, String message) {}

    /**
     * @param openid 当前小程序会话 openid（canonicalId）。
     * @param code   getPhoneNumber 回调动态令牌。
     */
    @Transactional
    public LinkResult link(String openid, String code) {
        String phone = phoneClient.getPhoneNumber(code);
        if (phone == null || phone.isBlank()) {
            log.info("手机号核验失败（微信未返回手机号）openid={}", openid);
            return new LinkResult(false, "手机号核验失败，请稍后重试");
        }

        String phoneHash = cipher.phoneHash(phone.trim());
        KycRecord match = kycRepo
                .findFirstByPhoneHashAndStatusOrderByVerifiedAtDesc(phoneHash, KycStatus.PASS)
                .orElse(null);
        if (match == null) {
            log.info("手机号核验通过但无可关联的实名订单 openid={}", openid);
            return new LinkResult(false, "该手机号下暂无可关联的实名订单");
        }

        String idCardHash = resolveIdCardHash(match);
        if (idCardHash == null || idCardHash.isBlank()) {
            log.info("手机号核验命中实名记录但无可用 id_card_hash openid={}", openid);
            return new LinkResult(false, "该手机号下暂无可关联的实名订单");
        }
        // 本端已有实名(PASS)即天然同人，无需再建关联，幂等返回成功。
        boolean alreadyKyc = kycRepo
                .findFirstByOpenidAndStatusOrderByVerifiedAtDesc(openid, KycStatus.PASS)
                .map(r -> idCardHash.equals(r.getIdCardHash()))
                .orElse(false);
        if (!alreadyKyc) {
            linkRepo.findByOpenid(openid).ifPresentOrElse(
                    existing -> {
                        existing.relink(idCardHash);
                        linkRepo.save(existing);
                    },
                    () -> linkRepo.save(IdentityLink.phone(openid, idCardHash)));
        }
        log.info("手机号核验跨端关联成功 openid={}", openid);
        return new LinkResult(true, "关联成功，已为你合并历史订单");
    }

    /**
     * 取实名记录的 id_card_hash；存量 PASS（V022 之前）该列为空时，就地解密 id_card_no_enc 重算并持久化，
     * 使关联与订单聚合即时可用（与 KycHashBackfillRunner 同算法，互为兜底）。
     */
    private String resolveIdCardHash(KycRecord match) {
        String hash = match.getIdCardHash();
        if (hash != null && !hash.isBlank()) {
            return hash;
        }
        if (match.getIdCardNoEnc() == null) {
            return null;
        }
        try {
            String idNo = cipher.decrypt(match.getIdCardNoEnc());
            if (idNo == null || idNo.isBlank()) {
                return null;
            }
            String computed = cipher.idCardHash(idNo.trim().toUpperCase());
            match.bindIdCardHash(computed);
            kycRepo.save(match);
            return computed;
        } catch (Exception e) {
            log.warn("就地补算 id_card_hash 失败 recordId={}: {}", match.getId(), e.getMessage());
            return null;
        }
    }
}


package com.sanshuiyuan.cend.identity;

import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import com.sanshuiyuan.cend.checkout.infra.repository.KycRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 跨端身份归并：把"同证同人"在多端（公众号 H5 / 小程序）各自的 openid 解析为同一自然人名下的 openid 集合，
 * 供"我的订单"等按自然人聚合（让小程序看得到同一用户在 H5 下的订单，反之亦然）。
 *
 * <p><b>身份焊接键</b>：已活体核验的实名记录的 {@code id_card_hash}（HMAC-SHA256，V022）。一证一号放宽为
 * "同证同人则链接"后，同一自然人可在不同 openid 下各有一条 {@link KycStatus#PASS} 记录、共享同一 hash，
 * 本类据此把这些 openid 归并为一个自然人。
 *
 * <p><b>安全铁律</b>：聚合<b>只</b>基于 PASS（已活体核验、人脸↔证件绑定）记录，<b>绝不</b>使用 INIT
 * （INIT 阶段任何人都能填他人身份证号，按其 hash 聚合 = 越权读他人订单）。当前 openid 自身恒在结果内；
 * 当前 openid 未实名（无 PASS）时只返回其自身，自然降级为"只看本端订单"。
 */
@Service
public class IdentityResolver {

    private final KycRecordRepository kycRepo;
    private final IdentityLinkRepository linkRepo;

    public IdentityResolver(KycRecordRepository kycRepo, IdentityLinkRepository linkRepo) {
        this.kycRepo = kycRepo;
        this.linkRepo = linkRepo;
    }

    /**
     * 解析当前 openid 所属自然人名下的全部 openid（含自身）。
     *
     * @param openid 当前会话统一身份键（canonicalId，即 h5_orders.openid 的归属值）。
     * @return 同一自然人的 openid 集合；至少包含传入 openid。保持 {@link LinkedHashSet} 以自身优先、顺序稳定。
     */
    @Transactional(readOnly = true)
    public Set<String> resolveOwnedOpenids(String openid) {
        Set<String> owned = new LinkedHashSet<>();
        if (openid == null || openid.isBlank()) {
            return owned;
        }
        owned.add(openid);

        // 解析当前 openid 所属自然人的 id_card_hash：
        //   优先取本端已 PASS 的实名记录；没有再看本端的"微信手机号核验"关联（identity_links）。
        // 无任何凭据 → 只看本端（降级），不暴露他人订单。
        String idCardHash = kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc(openid, KycStatus.PASS)
                .map(KycRecord::getIdCardHash)
                .filter(h -> h != null && !h.isBlank())
                .orElseGet(() -> linkRepo.findByOpenid(openid)
                        .map(IdentityLink::getIdCardHash)
                        .orElse(null));
        if (idCardHash == null || idCardHash.isBlank()) {
            return owned;
        }

        // 汇总同一自然人名下全部 openid：PASS 实名记录 ∪ 手机号核验关联。
        kycRepo.findAllByIdCardHashAndStatus(idCardHash, KycStatus.PASS)
                .forEach(rec -> addOpenid(owned, rec.getOpenid()));
        linkRepo.findAllByIdCardHash(idCardHash)
                .forEach(link -> addOpenid(owned, link.getOpenid()));
        return owned;
    }

    private static void addOpenid(Set<String> set, String openid) {
        if (openid != null && !openid.isBlank()) {
            set.add(openid);
        }
    }

    /**
     * 归属判定（按自然人聚合）：{@code targetOpenid}（订单归属 openid）是否属于 {@code currentOpenid}
     * 所属自然人。用于"我的订单"读路径（详情/资产/发票/状态）放行同人跨端访问；
     * 未实名时集合仅含自身，等价于严格相等校验。
     */
    @Transactional(readOnly = true)
    public boolean owns(String currentOpenid, String targetOpenid) {
        return targetOpenid != null && resolveOwnedOpenids(currentOpenid).contains(targetOpenid);
    }
}

package com.sanshuiyuan.h5.referral;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * H5 关系链绑定服务（008b）。在微信网页授权登录时定位/创建本人，并在<b>首次注册</b>时建立 L1/L2 关系链。
 *
 * <p><b>合规铁律</b>：
 * <ul>
 *   <li>关系链<b>仅首次注册写入</b>；已注册用户再次携带 ref_id 登录，绝不触碰其 {@code inviter_id}/{@code grand_inviter_id}；</li>
 *   <li>ref_id 解码失败按<b>自然流量</b>处理，绝不阻断登录注册；</li>
 *   <li>L2（{@code grandInviterId}）仅取「邀请人的 inviter_id」一次性快照，<b>不向上递归</b>（L3+ 物理隔离）。</li>
 * </ul>
 */
@Service
public class ReferralBindingService {

    private static final Logger log = LoggerFactory.getLogger(ReferralBindingService.class);

    private final H5UserRepository userRepo;
    private final RefIdCodec refIdCodec;

    public ReferralBindingService(H5UserRepository userRepo, RefIdCodec refIdCodec) {
        this.userRepo = userRepo;
        this.refIdCodec = refIdCodec;
    }

    /**
     * 微信登录时定位本人（按 openid）；不存在则视为<b>首次注册</b>，创建用户并尝试用 {@code refId} 绑定 L1/L2。
     * 已存在用户直接返回，关系链字段保持不变。
     *
     * @return 当前 H5 用户（已落库）。
     */
    @Transactional
    public H5User onWxLogin(String openid, String refId) {
        var existing = userRepo.findByOpenid(openid);
        if (existing.isPresent()) {
            // 已注册用户：登录分支绝不触碰关系链（仅首次注册可写入）。
            return existing.get();
        }

        // 首次注册：先落库取得自身 id（自我邀请判定依赖该 id）。
        H5User user = userRepo.save(H5User.create(openid));
        bindReferralOnRegister(user, refId);
        return user;
    }

    /**
     * 首次注册时的关系链绑定：解码 refId → 查邀请人 → 写入 L1/L2。任一降级条件命中则按自然流量（不绑定、不报错）。
     */
    private void bindReferralOnRegister(H5User newUser, String refId) {
        Long inviterId = resolveInviterId(refId);
        if (inviterId == null) {
            return; // 自然流量：无 refId / 解码失败。
        }
        if (inviterId.equals(newUser.getId())) {
            // 自我邀请：refId 解出自身 user_id，忽略（不绑定，按自然流量）。
            return;
        }
        H5User inviter = userRepo.findById(inviterId).orElse(null);
        if (inviter == null) {
            return; // 邀请人不存在：按自然流量处理。
        }
        // L2 仅取邀请人的 inviter_id（一次性快照），严禁继续向上追溯（L3+ 物理隔离）。
        Long grandInviterId = inviter.getInviterId();
        newUser.bindReferral(inviterId, grandInviterId);
        userRepo.save(newUser);
    }

    /**
     * 解码 refId 得到邀请人 user_id；空/非法/篡改一律返回 null（自然流量降级，不抛出、不阻断）。
     */
    private Long resolveInviterId(String refId) {
        if (refId == null || refId.isBlank()) {
            return null;
        }
        try {
            return refIdCodec.decode(refId);
        } catch (InvalidRefIdException e) {
            // 伪造/篡改/格式非法：按自然流量处理，仅记审计日志，不暴露细节、不阻断注册。
            log.info("ref_id 解码失败，按自然流量注册");
            return null;
        }
    }
}

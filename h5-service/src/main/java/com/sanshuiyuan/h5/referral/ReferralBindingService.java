package com.sanshuiyuan.h5.referral;

import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * H5 关系链绑定服务（008b / 014）。微信网页授权登录时仅定位/创建本人；L1/L2 关系链改由用户在落地页
 * <b>显式确认</b>后经 {@link #confirmBinding(String, String)} 一次性写入（spec 014 邀请确认）。
 *
 * <p><b>合规铁律</b>：
 * <ul>
 *   <li>关系链<b>仅首次绑定写入</b>；已绑定用户再次确认幂等返回，绝不触碰其 {@code inviter_id}/{@code grand_inviter_id}；</li>
 *   <li>ref_id 解码失败按<b>自然流量</b>处理，绝不阻断登录；</li>
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
     * 微信登录时定位本人（按 openid）；不存在则视为首次登录，<b>仅创建用户、不绑定关系链</b>，
     * 并写入微信昵称/头像资料快照（014）。关系链绑定改由用户显式确认后经
     * {@link #confirmBinding(String, String)} 触发。
     *
     * @return 当前 H5 用户（已落库）。
     */
    @Transactional
    public H5User onWxLogin(String openid, String nickname, String avatarUrl) {
        H5User user = userRepo.findByOpenid(openid)
                .orElseGet(() -> userRepo.save(H5User.create(openid)));
        // 刷新资料快照（仅资料，绝不触碰关系链）；无变更则不落库。
        if (user.updateProfile(nickname, avatarUrl)) {
            userRepo.save(user);
        }
        return user;
    }

    /**
     * 用户在落地页<b>显式确认</b>邀请后绑定 L1/L2 关系链（spec 014）。仅首次绑定可写、幂等。
     *
     * <p>解码 refId → 查邀请人 → 写入 L1/L2；任一降级条件（解码失败/邀请人不存在/自我邀请/已绑定）
     * 命中则不绑定、不报错。
     *
     * @return {@code true} 表示本次新建立了绑定；{@code false} 表示已绑定（幂等）或降级未绑定。
     */
    @Transactional
    public boolean confirmBinding(String openid, String refId) {
        H5User user = userRepo.findByOpenid(openid)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        if (user.getInviterId() != null) {
            return false; // 已绑定：幂等，绝不覆盖既有关系链。
        }
        Long inviterId = resolveInviterId(refId);
        if (inviterId == null) {
            return false; // 自然流量：无 refId / 解码失败。
        }
        if (inviterId.equals(user.getId())) {
            return false; // 自我邀请：refId 解出自身 user_id，忽略。
        }
        H5User inviter = userRepo.findById(inviterId).orElse(null);
        if (inviter == null) {
            return false; // 邀请人不存在：按自然流量处理。
        }
        // L2 仅取邀请人的 inviter_id（一次性快照），严禁继续向上追溯（L3+ 物理隔离）。
        Long grandInviterId = inviter.getInviterId();
        user.bindReferral(inviterId, grandInviterId);
        userRepo.save(user);
        return true;
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

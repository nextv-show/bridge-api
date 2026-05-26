package com.sanshuiyuan.user.application;

import com.sanshuiyuan.user.api.dto.SyncH5Response;
import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * H5 账号同步用例（spec 012）：将 H5 登录用户并入统一用户体系。
 *
 * <p>合规铁律：inviterId 仅在<b>首次创建</b>时写入 L1/L2 关系链；命中已存在用户一律不改写关系链。
 */
@Service
public class SyncH5UseCase {

    private final UserRepository userRepository;
    private final WxLoginUseCase wxLoginUseCase;

    public SyncH5UseCase(UserRepository userRepository, WxLoginUseCase wxLoginUseCase) {
        this.userRepository = userRepository;
        this.wxLoginUseCase = wxLoginUseCase;
    }

    @Transactional
    public SyncH5Response sync(String openid, String unionid, Long inviterId) {
        // 1) 有 unionid：按 unionid 命中即返回（关系链不动）。
        if (unionid != null && !unionid.isBlank()) {
            var byUnionid = userRepository.findByUnionid(unionid);
            if (byUnionid.isPresent()) {
                User user = byUnionid.get();
                return new SyncH5Response(user.getId(), false, user.getInviterId() != null);
            }
        } else {
            // 2) 无 unionid：按 openid 命中即返回。
            var byOpenid = userRepository.findByOpenidWx(openid);
            if (byOpenid.isPresent()) {
                User user = byOpenid.get();
                return new SyncH5Response(user.getId(), false, user.getInviterId() != null);
            }
        }

        // 3) 未命中：创建新用户（关系链一次性写入逻辑见 T12.2）。
        User created = wxLoginUseCase.createUserForSync(unionid, openid, inviterId, null);
        return new SyncH5Response(created.getId(), true, created.getInviterId() != null);
    }
}

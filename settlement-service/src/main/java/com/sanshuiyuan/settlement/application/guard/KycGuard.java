package com.sanshuiyuan.settlement.application.guard;

import com.sanshuiyuan.settlement.infra.user.UserEntity;
import com.sanshuiyuan.settlement.infra.user.UserRepository;
import org.springframework.stereotype.Component;

/** KYC 实名校验：未实名禁止提现。 */
@Component
public class KycGuard {
    private final UserRepository userRepository;

    public KycGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 校验 KYC 状态，未实名抛 KYC_NOT_VERIFIED。V1 仅检查 'VERIFIED' 状态。 */
    public void verify(Long userId) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null || !"VERIFIED".equals(user.getKycStatus())) {
            throw new KycNotVerifiedException(userId);
        }
    }
}

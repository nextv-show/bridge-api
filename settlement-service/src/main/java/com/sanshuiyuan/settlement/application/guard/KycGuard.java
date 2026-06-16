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

    /** 校验 KYC 状态，未实名抛 KYC_NOT_VERIFIED。users.kyc_status 实际取值为 'PASS'（实名通过）/'NONE'。 */
    public void verify(Long userId) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null || !"PASS".equals(user.getKycStatus())) {
            throw new KycNotVerifiedException(userId);
        }
    }
}

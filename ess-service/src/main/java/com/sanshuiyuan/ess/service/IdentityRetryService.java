package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.ContractIdentityVerification;
import com.sanshuiyuan.ess.infra.repository.ContractIdentityVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 身份核验重试限制服务。
 * <p>
 * 核心规则：
 * - 每日最多 3 次重试（重试次数按自然日计算）
 * - 每次失败记录详细失败原因
 * - 重试次数用尽后阻断新的核验请求
 */
@Service
public class IdentityRetryService {

    private static final Logger log = LoggerFactory.getLogger(IdentityRetryService.class);

    /** 每日最大重试次数 */
    static final int MAX_RETRIES_PER_DAY = 3;

    private final ContractIdentityVerificationRepository verificationRepository;

    public IdentityRetryService(ContractIdentityVerificationRepository verificationRepository) {
        this.verificationRepository = verificationRepository;
    }

    /**
     * 检查用户当日是否还可以进行身份核验。
     *
     * @param userId 用户 ID
     * @return 剩余重试次数（0 表示不可重试）
     */
    @Transactional(readOnly = true)
    public int getRemainingRetries(Long userId) {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        long todayAttempts = verificationRepository
                .countByUserIdAndCreatedAtAfter(userId, todayStart);
        int remaining = (int) Math.max(0, MAX_RETRIES_PER_DAY - todayAttempts);

        log.debug("用户 {} 当日已核验 {} 次，剩余 {} 次", userId, todayAttempts, remaining);
        return remaining;
    }

    /**
     * 校验用户是否可以重试，不可重试则抛出异常。
     *
     * @param userId 用户 ID
     * @throws IllegalStateException 如果超过重试限制
     */
    public void checkCanRetry(Long userId) {
        int remaining = getRemainingRetries(userId);
        if (remaining <= 0) {
            throw new IllegalStateException(
                    String.format("今日身份核验次数已达上限（%d次），请明天再试",
                            MAX_RETRIES_PER_DAY));
        }
    }

    /**
     * 记录核验失败原因。
     *
     * @param verificationId 核验记录 ID
     * @param failureReason  失败原因
     */
    @Transactional
    public void recordFailure(Long verificationId, String failureReason) {
        ContractIdentityVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new IllegalStateException("核验记录不存在: " + verificationId));

        verification.fail(failureReason);
        verificationRepository.save(verification);

        log.warn("身份核验失败 [contractId={}, userId={}, reason={}, retryCount={}]",
                verification.getContractId(), verification.getUserId(),
                failureReason, verification.getRetryCount());
    }

    /**
     * 获取用户当日失败的核验记录。
     *
     * @param userId 用户 ID
     * @return 失败记录列表
     */
    @Transactional(readOnly = true)
    public List<ContractIdentityVerification> getTodayFailures(Long userId) {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        return verificationRepository.findByUserIdAndStatusAndCreatedAtAfter(
                userId, ContractIdentityVerification.Status.FAILED, todayStart);
    }
}

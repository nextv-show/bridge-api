package com.sanshuiyuan.settlement.application.guard;

import com.sanshuiyuan.settlement.domain.WithdrawalPolicy;
import com.sanshuiyuan.settlement.infra.repository.WithdrawalOrderRepository;
import com.sanshuiyuan.settlement.infra.repository.WithdrawalPolicyRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 提现限额校验：单笔上限 + 单日累计上限，返回当前生效策略参数。 */
@Component
public class WithdrawalLimitGuard {
    private final WithdrawalPolicyRepository policyRepository;
    private final WithdrawalOrderRepository orderRepository;

    public WithdrawalLimitGuard(WithdrawalPolicyRepository policyRepository,
                                WithdrawalOrderRepository orderRepository) {
        this.policyRepository = policyRepository;
        this.orderRepository = orderRepository;
    }

    public WithdrawalLimit verify(Long userId, Long grossCents) {
        WithdrawalPolicy policy = policyRepository.findTopByOrderByEffectiveFromDesc()
                .orElseThrow(() -> new IllegalStateException("No withdrawal policy configured"));

        // 最低金额
        if (grossCents < policy.getMinCents()) {
            throw new BelowMinimumException(userId, grossCents, policy.getMinCents());
        }

        // 单笔限额
        if (grossCents > policy.getSingleMaxCents()) {
            throw new SingleLimitExceededException(userId, grossCents, policy.getSingleMaxCents());
        }

        // 单日次数（用时间范围避免 DB 时区转换差异）
        LocalDate today = LocalDate.now();
        long todayCount = orderRepository.countByUserIdAndDate(userId,
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        if (todayCount >= policy.getDailyMaxCount()) {
            throw new DailyCountExceededException(userId, todayCount, policy.getDailyMaxCount());
        }

        // 单日累计
        Long todayTotal = orderRepository.sumGrossCentsByUserIdAndDate(userId, LocalDate.now());
        todayTotal = todayTotal != null ? todayTotal : 0L;
        if (todayTotal + grossCents > policy.getDailyMaxCents()) {
            throw new DailyLimitExceededException(userId, todayTotal, grossCents, policy.getDailyMaxCents());
        }

        return new WithdrawalLimit(policy.getFeeBp(), policy.getSingleMaxCents(), policy.getDailyMaxCents(),
                policy.getDailyMaxCount(), policy.getMinCents());
    }

    public record WithdrawalLimit(int feeBp, long singleMaxCents, long dailyMaxCents,
                                  int dailyMaxCount, long minCents) {}
}

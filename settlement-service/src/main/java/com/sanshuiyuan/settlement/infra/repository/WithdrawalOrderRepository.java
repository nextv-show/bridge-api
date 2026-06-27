package com.sanshuiyuan.settlement.infra.repository;

import com.sanshuiyuan.settlement.domain.WithdrawalOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WithdrawalOrderRepository extends JpaRepository<WithdrawalOrder, Long> {
    Optional<WithdrawalOrder> findByUserIdAndClientRequestId(Long userId, String clientRequestId);
    List<WithdrawalOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<WithdrawalOrder> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 单日已申请总额（不含 FAILED），用于单日限额校验。 */
    @Query("SELECT COALESCE(SUM(w.grossCents), 0) FROM WithdrawalOrder w "
            + "WHERE w.userId = :userId AND FUNCTION('DATE', w.createdAt) = :date "
            + "AND w.status <> com.sanshuiyuan.settlement.domain.WithdrawalStatus.FAILED")
    Long sumGrossCentsByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    /** 单日已申请笔数（不含 FAILED），用于单日次数限额校验。 */
    @Query("SELECT COUNT(w) FROM WithdrawalOrder w "
            + "WHERE w.userId = :userId AND FUNCTION('DATE', w.createdAt) = :date "
            + "AND w.status <> com.sanshuiyuan.settlement.domain.WithdrawalStatus.FAILED")
    long countByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);
}

package com.sanshuiyuan.logistics.infra;

import com.sanshuiyuan.logistics.domain.LogisticsOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LogisticsOrderRepository extends JpaRepository<LogisticsOrder, Long> {

    Optional<LogisticsOrder> findByRequestId(Long requestId);

    boolean existsByRequestId(Long requestId);

    boolean existsByOutboxId(Long outboxId);
}

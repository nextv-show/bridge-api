package com.sanshuiyuan.logistics.infra;

import com.sanshuiyuan.logistics.domain.LogisticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LogisticsEventRepository extends JpaRepository<LogisticsEvent, Long> {

    List<LogisticsEvent> findByLogisticsOrderIdOrderByOccurredAtAsc(Long logisticsOrderId);

    boolean existsByExternalEventId(String externalEventId);
}

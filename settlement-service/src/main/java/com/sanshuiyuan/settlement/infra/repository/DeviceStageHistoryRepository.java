package com.sanshuiyuan.settlement.infra.repository;

import com.sanshuiyuan.settlement.domain.DeviceStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceStageHistoryRepository extends JpaRepository<DeviceStageHistory, Long> {
    List<DeviceStageHistory> findBySnOrderByOccurredAtDesc(String sn);
}

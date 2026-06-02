package com.sanshuiyuan.matching.logistics.infra;

import com.sanshuiyuan.matching.logistics.domain.LogisticsOutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LogisticsOutboxRepository extends JpaRepository<LogisticsOutboxEntry, Long> {

    List<LogisticsOutboxEntry> findByConsumedAtIsNullOrderByCreatedAtAsc();

    boolean existsByRequestIdAndDeviceAssetId(Long requestId, Long deviceAssetId);
}

package com.sanshuiyuan.water.device.infra;

import com.sanshuiyuan.water.device.domain.DeviceControlEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceControlEventRepository extends JpaRepository<DeviceControlEvent, Long> {

    List<DeviceControlEvent> findByEventTypeAndConsumedByWaterIsNullOrderByCreatedAtAsc(String eventType);
}

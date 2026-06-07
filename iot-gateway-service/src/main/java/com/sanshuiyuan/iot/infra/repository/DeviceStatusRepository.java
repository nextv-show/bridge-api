package com.sanshuiyuan.iot.infra.repository;

import com.sanshuiyuan.iot.domain.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceStatusRepository extends JpaRepository<DeviceStatus, String> {

    Optional<DeviceStatus> findBySn(String sn);
}

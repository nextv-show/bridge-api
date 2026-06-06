package com.sanshuiyuan.water.device.infra;

import com.sanshuiyuan.water.device.domain.DevicePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DevicePermissionRepository extends JpaRepository<DevicePermission, String> {

    Optional<DevicePermission> findBySn(String sn);
}

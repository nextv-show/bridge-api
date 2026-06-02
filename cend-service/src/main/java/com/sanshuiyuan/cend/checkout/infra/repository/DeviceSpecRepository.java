package com.sanshuiyuan.cend.checkout.infra.repository;

import com.sanshuiyuan.cend.checkout.domain.DeviceSpec;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceSpecRepository extends JpaRepository<DeviceSpec, Long> {

    List<DeviceSpec> findByStatus(DeviceSpec.SpecStatus status);

    Optional<DeviceSpec> findBySpecId(String specId);

    Optional<DeviceSpec> findBySpecIdAndStatus(String specId, DeviceSpec.SpecStatus status);

    Optional<DeviceSpec> findByModelCode(String modelCode);
}

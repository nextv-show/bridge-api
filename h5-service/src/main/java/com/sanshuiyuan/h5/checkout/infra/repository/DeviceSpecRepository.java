package com.sanshuiyuan.h5.checkout.infra.repository;

import com.sanshuiyuan.h5.checkout.domain.DeviceSpec;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceSpecRepository extends JpaRepository<DeviceSpec, Long> {

    List<DeviceSpec> findByStatus(DeviceSpec.SpecStatus status);

    Optional<DeviceSpec> findBySpecIdAndStatus(String specId, DeviceSpec.SpecStatus status);
}

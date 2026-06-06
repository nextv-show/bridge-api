package com.sanshuiyuan.iot.infra.repository;

import com.sanshuiyuan.iot.domain.DeviceSecret;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceSecretRepository extends JpaRepository<DeviceSecret, String> {

    Optional<DeviceSecret> findBySn(String sn);

    Optional<DeviceSecret> findBySnAndRevokedAtIsNull(String sn);
}

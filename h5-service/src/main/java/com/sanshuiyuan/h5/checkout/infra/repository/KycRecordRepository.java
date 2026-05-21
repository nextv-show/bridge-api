package com.sanshuiyuan.h5.checkout.infra.repository;

import com.sanshuiyuan.h5.checkout.domain.KycRecord;
import com.sanshuiyuan.h5.checkout.domain.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KycRecordRepository extends JpaRepository<KycRecord, Long> {

    Optional<KycRecord> findFirstByOpenidAndStatusOrderByVerifiedAtDesc(String openid, KycStatus status);
}

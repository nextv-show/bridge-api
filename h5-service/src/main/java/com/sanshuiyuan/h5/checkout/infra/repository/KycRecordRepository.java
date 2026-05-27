package com.sanshuiyuan.h5.checkout.infra.repository;

import com.sanshuiyuan.h5.checkout.domain.KycRecord;
import com.sanshuiyuan.h5.checkout.domain.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycRecordRepository extends JpaRepository<KycRecord, Long> {

    Optional<KycRecord> findFirstByOpenidAndStatusOrderByVerifiedAtDesc(String openid, KycStatus status);

    List<KycRecord> findAllByOpenidAndStatus(String openid, KycStatus status);

    Optional<KycRecord> findFirstByCertifyIdAndOpenidAndStatus(String certifyId, String openid, KycStatus status);

    /** 一证一号：同一身份证哈希在「非该 openid」下是否已存在指定状态（PASS）记录。 */
    boolean existsByIdCardHashAndStatusAndOpenidNot(String idCardHash, KycStatus status, String openid);
}

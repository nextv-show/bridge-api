package com.sanshuiyuan.admin.infra.repository;

import com.sanshuiyuan.admin.domain.KycRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KycRecordRepository extends JpaRepository<KycRecord, Long> {

    Page<KycRecord> findByStatus(KycRecord.Status status, Pageable pageable);

    Page<KycRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(KycRecord.Status status);

    /** 批量取一组 openid 的全部 KYC 记录，供用户列表/详情实时派生有效实名状态。 */
    List<KycRecord> findByOpenidIn(Collection<String> openids);
}

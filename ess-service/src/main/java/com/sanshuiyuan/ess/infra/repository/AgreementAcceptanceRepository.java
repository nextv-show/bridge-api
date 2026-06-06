package com.sanshuiyuan.ess.infra.repository;

import com.sanshuiyuan.ess.domain.AgreementAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgreementAcceptanceRepository extends JpaRepository<AgreementAcceptance, Long> {

    List<AgreementAcceptance> findByOpenidOrderByAcceptedAtDesc(String openid);

    Optional<AgreementAcceptance> findTopByOpenidAndAgreementCodeOrderByAcceptedAtDesc(String openid, String agreementCode);

    boolean existsByOpenidAndAgreementCode(String openid, String agreementCode);
}

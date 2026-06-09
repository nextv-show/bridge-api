package com.sanshuiyuan.ess.infra.repository;

import com.sanshuiyuan.ess.domain.AgreementAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgreementAcceptanceRepository extends JpaRepository<AgreementAcceptance, Long> {

    List<AgreementAcceptance> findByOpenidOrderByAcceptedAtDesc(String openid);

    Optional<AgreementAcceptance> findTopByOpenidAndAgreementCodeOrderByAcceptedAtDesc(String openid, String agreementCode);

    /** 幂等查询：唯一键 (openid, agreement_code, template_version) 对应的已有记录。 */
    Optional<AgreementAcceptance> findByOpenidAndAgreementCodeAndTemplateVersion(
            String openid, String agreementCode, int templateVersion);

    boolean existsByOpenidAndAgreementCode(String openid, String agreementCode);
}

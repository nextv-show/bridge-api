package com.sanshuiyuan.ess.infra.repository;

import com.sanshuiyuan.ess.domain.ContractTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Long> {

    Optional<ContractTemplate> findByTemplateCodeAndVersion(String templateCode, int version);

    Optional<ContractTemplate> findTopByTemplateCodeOrderByVersionDesc(String templateCode);

    List<ContractTemplate> findByTemplateCodeOrderByVersionDesc(String templateCode);

    List<ContractTemplate> findByTemplateType(ContractTemplate.TemplateType templateType);

    List<ContractTemplate> findByTemplateTypeAndIsDeprecatedFalse(ContractTemplate.TemplateType templateType);
}

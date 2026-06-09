package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.AgreementAcceptance;
import com.sanshuiyuan.ess.domain.ContractTemplate;
import com.sanshuiyuan.ess.infra.repository.AgreementAcceptanceRepository;
import com.sanshuiyuan.ess.infra.repository.ContractTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 协议服务。
 * <p>
 * 提供协议类模板（{@link ContractTemplate.TemplateType#AGREEMENT}）的查询能力，
 * 以及用户同意记录的写入与查询能力。
 */
@Service
public class AgreementService {

    private static final Logger log = LoggerFactory.getLogger(AgreementService.class);

    private final ContractTemplateService templateService;
    private final ContractTemplateRepository templateRepository;
    private final AgreementAcceptanceRepository acceptanceRepository;

    public AgreementService(ContractTemplateService templateService,
                            ContractTemplateRepository templateRepository,
                            AgreementAcceptanceRepository acceptanceRepository) {
        this.templateService = templateService;
        this.templateRepository = templateRepository;
        this.acceptanceRepository = acceptanceRepository;
    }

    /**
     * 获取协议最新版本（委托给 {@link ContractTemplateService#getLatestVersion}）。
     */
    @Transactional(readOnly = true)
    public ContractTemplate getLatestAgreement(String agreementCode) {
        return templateService.getLatestVersion(agreementCode);
    }

    /**
     * 获取所有活跃协议（未废弃的协议类模板）。
     */
    @Transactional(readOnly = true)
    public List<ContractTemplate> getAllActiveAgreements() {
        return templateRepository.findByTemplateTypeAndIsDeprecatedFalse(
                ContractTemplate.TemplateType.AGREEMENT);
    }

    /**
     * 记录用户同意协议。
     * <p>
     * 先校验协议模板存在（取最新版本），再写入同意记录，记录的版本号为当前最新版本。
     *
     * @return 已保存的同意记录
     */
    @Transactional
    public AgreementAcceptance recordAcceptance(String openid, String agreementCode,
                                                String clientType, String clientIp) {
        ContractTemplate agreement = getLatestAgreement(agreementCode);

        // 幂等：同一 (openid, code, version) 重复接受不报错（唯一键 uk_openid_agreement_version），
        // 直接返回既有记录，避免重复点击/重进时撞 Duplicate entry 抛 500。
        return acceptanceRepository
                .findByOpenidAndAgreementCodeAndTemplateVersion(openid, agreementCode, agreement.getVersion())
                .map(existing -> {
                    log.info("协议已接受，幂等返回 [openid={}, code={}, version={}]",
                            openid, agreementCode, agreement.getVersion());
                    return existing;
                })
                .orElseGet(() -> {
                    AgreementAcceptance saved = acceptanceRepository.save(AgreementAcceptance.create(
                            openid, agreementCode, agreement.getVersion(), clientType, clientIp));
                    log.info("协议同意已记录 [openid={}, code={}, version={}, clientType={}]",
                            openid, agreementCode, agreement.getVersion(), clientType);
                    return saved;
                });
    }

    /**
     * 获取用户的所有协议同意记录（按同意时间倒序）。
     */
    @Transactional(readOnly = true)
    public List<AgreementAcceptance> getUserAcceptances(String openid) {
        return acceptanceRepository.findByOpenidOrderByAcceptedAtDesc(openid);
    }

    /**
     * 检查用户是否已同意指定协议。
     */
    @Transactional(readOnly = true)
    public boolean hasAccepted(String openid, String agreementCode) {
        return acceptanceRepository.existsByOpenidAndAgreementCode(openid, agreementCode);
    }
}

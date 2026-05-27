package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.ContractTemplate;
import com.sanshuiyuan.ess.infra.repository.ContractTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 合同模板服务。
 * <p>
 * 提供模板 CRUD 与版本管理能力。
 */
@Service
public class ContractTemplateService {

    private static final Logger log = LoggerFactory.getLogger(ContractTemplateService.class);

    private final ContractTemplateRepository templateRepository;

    public ContractTemplateService(ContractTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /**
     * 创建新模板。
     */
    @Transactional
    public ContractTemplate createTemplate(String templateCode, String templateName,
                                            ContractTemplate.TemplateType templateType,
                                            String contentBody) {
        // 查找当前最新版本
        int nextVersion = templateRepository
                .findTopByTemplateCodeOrderByVersionDesc(templateCode)
                .map(t -> t.getVersion() + 1)
                .orElse(1);

        ContractTemplate template = ContractTemplate.create(
                templateCode, templateName, templateType, contentBody, nextVersion);
        template = templateRepository.save(template);

        log.info("模板已创建 [code={}, name={}, version={}]",
                templateCode, templateName, nextVersion);
        return template;
    }

    /**
     * 更新模板内容（创建新版本）。
     */
    @Transactional
    public ContractTemplate updateTemplate(String templateCode, String contentBody) {
        ContractTemplate latest = getLatestVersion(templateCode);
        ContractTemplate newVersion = latest.newVersion(contentBody);
        newVersion = templateRepository.save(newVersion);

        log.info("模板已更新 [code={}, newVersion={}]",
                templateCode, newVersion.getVersion());
        return newVersion;
    }

    /**
     * 获取模板最新版本。
     */
    @Transactional(readOnly = true)
    public ContractTemplate getLatestVersion(String templateCode) {
        return templateRepository
                .findTopByTemplateCodeOrderByVersionDesc(templateCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "模板不存在: " + templateCode));
    }

    /**
     * 获取模板指定版本。
     */
    @Transactional(readOnly = true)
    public Optional<ContractTemplate> getVersion(String templateCode, int version) {
        return templateRepository.findByTemplateCodeAndVersion(templateCode, version);
    }

    /**
     * 获取模板所有版本。
     */
    @Transactional(readOnly = true)
    public List<ContractTemplate> getAllVersions(String templateCode) {
        return templateRepository.findByTemplateCodeOrderByVersionDesc(templateCode);
    }

    /**
     * 获取指定类型的所有模板（最新版本）。
     */
    @Transactional(readOnly = true)
    public List<ContractTemplate> getByType(ContractTemplate.TemplateType templateType) {
        return templateRepository.findByTemplateType(templateType);
    }

    /**
     * 根据 ID 获取模板。
     */
    @Transactional(readOnly = true)
    public ContractTemplate getById(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: id=" + id));
    }
}

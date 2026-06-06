package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.AgreementAcceptance;
import com.sanshuiyuan.ess.domain.ContractTemplate;
import com.sanshuiyuan.ess.infra.repository.AgreementAcceptanceRepository;
import com.sanshuiyuan.ess.infra.repository.ContractTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgreementService} 单元测试。
 * <p>
 * 纯 Mockito 单元测试，不加载 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class AgreementServiceTest {

    @Mock
    private ContractTemplateService templateService;

    @Mock
    private ContractTemplateRepository templateRepository;

    @Mock
    private AgreementAcceptanceRepository acceptanceRepository;

    private AgreementService service;

    @BeforeEach
    void setUp() {
        service = new AgreementService(templateService, templateRepository, acceptanceRepository);
    }

    private ContractTemplate agreementTemplate(String code, String name, int version) {
        return ContractTemplate.create(code, name, ContractTemplate.TemplateType.AGREEMENT,
                "协议正文内容", version);
    }

    // ========== getLatestAgreement ==========

    @Test
    void getLatestAgreement_whenFound_returnsTemplate() {
        ContractTemplate template = agreementTemplate("PRIVACY", "隐私政策", 3);
        when(templateService.getLatestVersion("PRIVACY")).thenReturn(template);

        ContractTemplate result = service.getLatestAgreement("PRIVACY");

        assertThat(result).isSameAs(template);
        assertThat(result.getTemplateCode()).isEqualTo("PRIVACY");
        assertThat(result.getVersion()).isEqualTo(3);
        verify(templateService).getLatestVersion("PRIVACY");
    }

    @Test
    void getLatestAgreement_whenNotFound_throwsIllegalArgument() {
        when(templateService.getLatestVersion("MISSING"))
                .thenThrow(new IllegalArgumentException("模板不存在: MISSING"));

        assertThatThrownBy(() -> service.getLatestAgreement("MISSING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模板不存在");
    }

    // ========== getAllActiveAgreements ==========

    @Test
    void getAllActiveAgreements_returnsAgreementTemplates() {
        ContractTemplate a = agreementTemplate("PRIVACY", "隐私政策", 1);
        ContractTemplate b = agreementTemplate("TERMS", "用户协议", 2);
        when(templateRepository.findByTemplateTypeAndIsDeprecatedFalse(
                ContractTemplate.TemplateType.AGREEMENT)).thenReturn(List.of(a, b));

        List<ContractTemplate> result = service.getAllActiveAgreements();

        assertThat(result).containsExactly(a, b);
        verify(templateRepository).findByTemplateTypeAndIsDeprecatedFalse(
                ContractTemplate.TemplateType.AGREEMENT);
    }

    @Test
    void getAllActiveAgreements_whenNone_returnsEmptyList() {
        when(templateRepository.findByTemplateTypeAndIsDeprecatedFalse(
                ContractTemplate.TemplateType.AGREEMENT)).thenReturn(Collections.emptyList());

        List<ContractTemplate> result = service.getAllActiveAgreements();

        assertThat(result).isEmpty();
    }

    // ========== recordAcceptance ==========

    @Test
    void recordAcceptance_whenTemplateExists_savesAcceptanceWithCorrectFields() {
        ContractTemplate template = agreementTemplate("PRIVACY", "隐私政策", 5);
        when(templateService.getLatestVersion("PRIVACY")).thenReturn(template);
        when(acceptanceRepository.save(any(AgreementAcceptance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AgreementAcceptance result = service.recordAcceptance(
                "openid-123", "PRIVACY", "MINIAPP", "203.0.113.9");

        ArgumentCaptor<AgreementAcceptance> captor = ArgumentCaptor.forClass(AgreementAcceptance.class);
        verify(acceptanceRepository).save(captor.capture());
        AgreementAcceptance saved = captor.getValue();

        assertThat(saved.getOpenid()).isEqualTo("openid-123");
        assertThat(saved.getAgreementCode()).isEqualTo("PRIVACY");
        assertThat(saved.getTemplateVersion()).isEqualTo(5);
        assertThat(saved.getClientType()).isEqualTo("MINIAPP");
        assertThat(saved.getClientIp()).isEqualTo("203.0.113.9");
        assertThat(result).isSameAs(saved);
    }

    @Test
    void recordAcceptance_whenTemplateMissing_throwsAndDoesNotSave() {
        when(templateService.getLatestVersion("MISSING"))
                .thenThrow(new IllegalArgumentException("模板不存在: MISSING"));

        assertThatThrownBy(() -> service.recordAcceptance(
                "openid-123", "MISSING", "H5", "203.0.113.9"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(acceptanceRepository, never()).save(any());
    }

    // ========== hasAccepted ==========

    @Test
    void hasAccepted_whenAcceptanceExists_returnsTrue() {
        when(acceptanceRepository.existsByOpenidAndAgreementCode("openid-123", "PRIVACY"))
                .thenReturn(true);

        assertThat(service.hasAccepted("openid-123", "PRIVACY")).isTrue();
    }

    @Test
    void hasAccepted_whenNoAcceptance_returnsFalse() {
        when(acceptanceRepository.existsByOpenidAndAgreementCode("openid-123", "PRIVACY"))
                .thenReturn(false);

        assertThat(service.hasAccepted("openid-123", "PRIVACY")).isFalse();
    }

    // ========== getUserAcceptances ==========

    @Test
    void getUserAcceptances_returnsAcceptancesForOpenid() {
        AgreementAcceptance a1 = AgreementAcceptance.create(
                "openid-123", "PRIVACY", 2, "H5", "203.0.113.9");
        AgreementAcceptance a2 = AgreementAcceptance.create(
                "openid-123", "TERMS", 1, "MINIAPP", "203.0.113.10");
        when(acceptanceRepository.findByOpenidOrderByAcceptedAtDesc("openid-123"))
                .thenReturn(List.of(a1, a2));

        List<AgreementAcceptance> result = service.getUserAcceptances("openid-123");

        assertThat(result).containsExactly(a1, a2);
        verify(acceptanceRepository).findByOpenidOrderByAcceptedAtDesc("openid-123");
    }
}

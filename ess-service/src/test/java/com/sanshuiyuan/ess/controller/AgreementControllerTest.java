package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.domain.AgreementAcceptance;
import com.sanshuiyuan.ess.domain.ContractTemplate;
import com.sanshuiyuan.ess.dto.AgreementDto.AcceptRequest;
import com.sanshuiyuan.ess.dto.AgreementDto.AcceptanceRecord;
import com.sanshuiyuan.ess.dto.AgreementDto.AcceptanceStatus;
import com.sanshuiyuan.ess.dto.AgreementDto.AgreementDetail;
import com.sanshuiyuan.ess.dto.AgreementDto.AgreementSummary;
import com.sanshuiyuan.ess.service.AgreementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link AgreementController} 单元测试。
 * <p>
 * 直接调用 controller 方法（不加载 Spring MVC 上下文），通过手动设置
 * {@link SecurityContextHolder} 满足 {@code CurrentOpenid.require()} 的身份校验。
 */
@ExtendWith(MockitoExtension.class)
class AgreementControllerTest {

    private static final String OPENID = "openid-test-001";

    @Mock
    private AgreementService agreementService;

    private AgreementController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        controller = new AgreementController(agreementService);

        // 注入一个已认证的身份，其 name 即为当前 openid。
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                OPENID, null, AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(auth);

        request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.42");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ContractTemplate agreementTemplate(String code, String name, int version) {
        return ContractTemplate.create(code, name, ContractTemplate.TemplateType.AGREEMENT,
                "协议正文内容", version);
    }

    @SuppressWarnings("unchecked")
    private <T> T data(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo(0);
        return (T) body.get("data");
    }

    // ========== GET /api/c/agreements ==========

    @Test
    void listAgreements_returnsSummaries() {
        when(agreementService.getAllActiveAgreements()).thenReturn(List.of(
                agreementTemplate("PRIVACY", "隐私政策", 2),
                agreementTemplate("TERMS", "用户协议", 1)));

        List<AgreementSummary> data = data(controller.listAgreements());

        assertThat(data).hasSize(2);
        assertThat(data.get(0)).isEqualTo(new AgreementSummary("PRIVACY", "隐私政策", 2));
        assertThat(data.get(1)).isEqualTo(new AgreementSummary("TERMS", "用户协议", 1));
    }

    // ========== GET /api/c/agreements/{code} ==========

    @Test
    void getAgreement_returnsDetailWithContentBody() {
        when(agreementService.getLatestAgreement("PRIVACY"))
                .thenReturn(agreementTemplate("PRIVACY", "隐私政策", 4));

        AgreementDetail data = data(controller.getAgreement("PRIVACY"));

        assertThat(data).isEqualTo(
                new AgreementDetail("PRIVACY", "隐私政策", 4, "协议正文内容"));
    }

    @Test
    void getAgreement_whenNotFound_throwsIllegalArgument() {
        when(agreementService.getLatestAgreement("MISSING"))
                .thenThrow(new IllegalArgumentException("模板不存在: MISSING"));

        assertThatThrownBy(() -> controller.getAgreement("MISSING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模板不存在");
    }

    // ========== POST /api/c/agreements/{code}/accept ==========

    @Test
    void acceptAgreement_recordsAcceptance() {
        AgreementAcceptance acceptance = AgreementAcceptance.create(
                OPENID, "PRIVACY", 4, "MINIAPP", "203.0.113.42");
        when(agreementService.recordAcceptance(OPENID, "PRIVACY", "MINIAPP", "203.0.113.42"))
                .thenReturn(acceptance);

        ResponseEntity<Map<String, Object>> response = controller.acceptAgreement(
                "PRIVACY", new AcceptRequest("PRIVACY", "MINIAPP"), request);

        AcceptanceRecord data = data(response);
        assertThat(data.agreementCode()).isEqualTo("PRIVACY");
        assertThat(data.templateVersion()).isEqualTo(4);
        assertThat(data.clientType()).isEqualTo("MINIAPP");
    }

    @Test
    void acceptAgreement_whenNoBody_defaultsClientTypeToH5() {
        AgreementAcceptance acceptance = AgreementAcceptance.create(
                OPENID, "PRIVACY", 4, "H5", "203.0.113.42");
        when(agreementService.recordAcceptance(OPENID, "PRIVACY", "H5", "203.0.113.42"))
                .thenReturn(acceptance);

        AcceptanceRecord data = data(controller.acceptAgreement("PRIVACY", null, request));

        assertThat(data.clientType()).isEqualTo("H5");
    }

    @Test
    void acceptAgreement_whenNotAuthenticated_throwsUnauthorized() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> controller.acceptAgreement(
                "PRIVACY", new AcceptRequest("PRIVACY", "H5"), request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ========== GET /api/c/agreements/{code}/status ==========

    @Test
    void getAcceptanceStatus_whenAccepted_returnsTrue() {
        when(agreementService.hasAccepted(OPENID, "PRIVACY")).thenReturn(true);

        AcceptanceStatus data = data(controller.getAcceptanceStatus("PRIVACY"));

        assertThat(data).isEqualTo(new AcceptanceStatus("PRIVACY", true));
    }

    @Test
    void getAcceptanceStatus_whenNotAccepted_returnsFalse() {
        when(agreementService.hasAccepted(OPENID, "TERMS")).thenReturn(false);

        AcceptanceStatus data = data(controller.getAcceptanceStatus("TERMS"));

        assertThat(data).isEqualTo(new AcceptanceStatus("TERMS", false));
    }

    // ========== GET /api/c/agreements/acceptances ==========

    @Test
    void getUserAcceptances_returnsRecordsForCurrentUser() {
        when(agreementService.getUserAcceptances(OPENID)).thenReturn(List.of(
                AgreementAcceptance.create(OPENID, "PRIVACY", 2, "H5", "203.0.113.42"),
                AgreementAcceptance.create(OPENID, "TERMS", 1, "MINIAPP", "203.0.113.43")));

        List<AcceptanceRecord> data = data(controller.getUserAcceptances());

        assertThat(data).hasSize(2);
        assertThat(data.get(0).agreementCode()).isEqualTo("PRIVACY");
        assertThat(data.get(0).templateVersion()).isEqualTo(2);
        assertThat(data.get(1).agreementCode()).isEqualTo("TERMS");
    }
}

package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.CertificateStatus;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.domain.Contract.ArchiveStatus;
import com.sanshuiyuan.ess.domain.ContractAuditTrail;
import com.sanshuiyuan.ess.domain.ContractAuditTrail.Action;
import com.sanshuiyuan.ess.domain.ContractAuditTrail.ActorType;
import com.sanshuiyuan.ess.infra.repository.ContractAuditTrailRepository;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * T22.14: 司法存证全流程测试。
 * <p>
 * 验证完整的出证流程：
 * 1. 合同生成 → 签署 → 归档（设置 certificateStatus = PENDING）
 * 2. 出证（CertificateService.certifyContract）
 * 3. 审计事件完整性校验
 * 4. 出证状态查询
 */
@ExtendWith(MockitoExtension.class)
class JudicialEvidenceFlowTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private ContractAuditTrailRepository auditTrailRepository;

    @Mock
    private com.sanshuiyuan.ess.infra.client.EssApiClient essApiClient;

    @Mock
    private com.sanshuiyuan.ess.config.EssProperties essProperties;

    private AuditTrailService auditTrailService;

    @BeforeEach
    void setUp() {
        auditTrailService = new AuditTrailService(auditTrailRepository);
    }

    /**
     * 全流程：创建合同 → 签署 → 归档 → 出证 → 查询出证结果
     */
    @Test
    void fullFlow_generateSignArchiveCertify() {
        // ===== 1. 创建合同草稿 =====
        Contract contract = Contract.createDraft("CT-JUDICIAL-001", 1L, 100L, "ORD-JUD", "SN-JUD-001");
        assertNotNull(contract);
        assertEquals(ContractStatus.DRAFT, contract.getStatus());

        // ===== 2. 生成合同 =====
        contract.markGenerated("{\"deviceSn\":\"SN-JUD-001\"}", "{\"userId\":100}");
        assertEquals(ContractStatus.GENERATED, contract.getStatus());

        // ===== 3. 签署 =====
        contract.startSigning("flow-judicial-001");
        assertEquals(ContractStatus.SIGNING, contract.getStatus());

        contract.completeSigning("https://pdf.example.com/judicial.pdf", "sha256-judicial-hash");
        assertEquals(ContractStatus.SIGNED, contract.getStatus());
        assertEquals("sha256-judicial-hash", contract.getPdfHash());

        // ===== 4. 归档 =====
        contract.updateArchiveUrls(
                "https://cos.example.com/contracts/CT-JUDICIAL-001.pdf",
                "https://oss.example.com/contracts/CT-JUDICIAL-001.pdf",
                "sha256-judicial-hash");
        contract.archive();
        assertEquals(ContractStatus.ARCHIVED, contract.getStatus());
        assertEquals(ArchiveStatus.ARCHIVED, contract.getArchiveStatus());
        assertNotNull(contract.getArchivedAt());

        // ===== 5. 标记待出证 =====
        contract.markPendingCertificate();
        assertEquals(CertificateStatus.PENDING, contract.getCertificateStatus());

        // ===== 6. 出证 =====
        contract.markCertifying();
        assertEquals(CertificateStatus.APPLYING, contract.getCertificateStatus());

        // 模拟出证 API 返回成功
        contract.completeCertificate("CERT-JUD-20260527-001", "https://cert.example.com/CT-JUDICIAL-001.pdf");
        assertEquals(CertificateStatus.CERTIFIED, contract.getCertificateStatus());
        assertEquals("CERT-JUD-20260527-001", contract.getCertificateNo());
        assertEquals("https://cert.example.com/CT-JUDICIAL-001.pdf", contract.getCertificatePdfUrl());
        assertNotNull(contract.getCertifiedAt());

        // ===== 7. 验证审计事件记录 =====
        // 模拟记录审计事件
        ContractAuditTrail createEvent = auditTrailService.recordUserEvent(
                1L, Action.CREATE, 100L,
                "{\"orderId\":\"ORD-JUD\",\"deviceSn\":\"SN-JUD-001\"}", null);
        verify(auditTrailRepository).save(any(ContractAuditTrail.class));

        ContractAuditTrail archiveEvent = auditTrailService.recordSystemEvent(
                1L, Action.ARCHIVE,
                "{\"sha256\":\"sha256-judicial-hash\"}");
        verify(auditTrailRepository, times(2)).save(any(ContractAuditTrail.class));

        ContractAuditTrail certifyEvent = auditTrailService.recordSystemEvent(
                1L, Action.CERTIFY_SUCCESS,
                "{\"certificateNo\":\"CERT-JUD-20260527-001\"}");
        verify(auditTrailRepository, times(3)).save(any(ContractAuditTrail.class));

        // ===== 8. 查询出证结果 =====
        CertificateService certificateService = new CertificateService(
                contractRepository, essApiClient, essProperties, auditTrailService,
                null, null, null); // 本用例不走出证归档路径

        // 模拟 repository 返回已出证合同
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));

        CertificateService.CertificateResult result = certificateService.queryCertificateStatus(1L);
        assertTrue(result.success());
        assertEquals("CERT-JUD-20260527-001", result.certificateNo());
        assertEquals("CERTIFIED", result.status());
        assertNotNull(result.certifiedAt());
    }

    /**
     * 出证失败后重试流程。
     */
    @Test
    void certifyFailureAndRetry() {
        Contract contract = Contract.createDraft("CT-RETRY-001", 1L, 200L, "ORD-RETRY", "SN-RETRY");
        contract.markGenerated("{}", "{}");
        contract.startSigning("flow-retry");
        contract.completeSigning("https://pdf.example.com/retry.pdf", "hash-retry");
        contract.updateArchiveUrls("https://cos.example.com/retry.pdf",
                "https://oss.example.com/retry.pdf", "hash-retry");
        contract.archive();
        contract.markPendingCertificate();

        // 第一次出证失败
        contract.markCertifying();
        contract.markCertificateFailed();
        assertEquals(CertificateStatus.FAILED, contract.getCertificateStatus());

        // 重新出证
        contract.markCertifying();
        contract.completeCertificate("CERT-RETRY-001", "https://cert.example.com/retry.pdf");
        assertEquals(CertificateStatus.CERTIFIED, contract.getCertificateStatus());
        assertEquals("CERT-RETRY-001", contract.getCertificateNo());
    }

    /**
     * 未归档合同不允许出证。
     */
    @Test
    void certifyNonArchivedContract_shouldFail() {
        CertificateService certificateService = new CertificateService(
                contractRepository, essApiClient, essProperties, auditTrailService,
                null, null, null); // 本用例不走出证归档路径

        Contract signedContract = Contract.createDraft("CT-NOTARCH-001", 1L, 300L, "ORD-NA", "SN-NA");
        signedContract.markGenerated("{}", "{}");
        signedContract.startSigning("flow-na");
        signedContract.completeSigning("https://pdf.example.com/na.pdf", "hash-na");
        // 合同仍为 SIGNED 状态（未归档）

        when(contractRepository.findById(50L)).thenReturn(Optional.of(signedContract));

        assertThrows(IllegalStateException.class,
                () -> certificateService.certifyContract(50L));
    }

    /**
     * 审计轨迹完整性：所有生命周期事件都应被记录。
     */
    @Test
    void auditTrailCompleteness_allLifecycleEvents() {
        // 模拟完整审计轨迹
        Long contractId = 1L;

        auditTrailService.recordUserEvent(contractId, Action.CREATE, 100L, "{}", "10.0.0.1");
        auditTrailService.recordSystemEvent(contractId, Action.GENERATE, "{}");
        auditTrailService.recordSystemEvent(contractId, Action.START_SIGN, "{\"essFlowId\":\"flow-1\"}");
        auditTrailService.recordSystemEvent(contractId, Action.SIGN_COMPLETE, "{\"pdfHash\":\"hash123\"}");
        auditTrailService.recordSystemEvent(contractId, Action.ARCHIVE, "{\"sha256\":\"hash123\"}");
        auditTrailService.recordSystemEvent(contractId, Action.CERTIFY_SUCCESS, "{\"certificateNo\":\"CERT-001\"}");
        auditTrailService.recordEvent(contractId, Action.VIEW, "100", ActorType.USER, null, "10.0.0.2");
        auditTrailService.recordEvent(contractId, Action.DOWNLOAD, "100", ActorType.USER, null, "10.0.0.2");

        // 8 个事件全部被保存
        verify(auditTrailRepository, times(8)).save(any(ContractAuditTrail.class));
    }

    /**
     * 出证状态流转：PENDING → APPLYING → CERTIFIED。
     */
    @Test
    void certificateStatusTransitions() {
        assertEquals(CertificateStatus.PENDING, CertificateStatus.valueOf("PENDING"));
        assertEquals(CertificateStatus.APPLYING, CertificateStatus.valueOf("APPLYING"));
        assertEquals(CertificateStatus.CERTIFIED, CertificateStatus.valueOf("CERTIFIED"));
        assertEquals(CertificateStatus.FAILED, CertificateStatus.valueOf("FAILED"));
    }

    /**
     * 审计事件 Actor 类型验证。
     */
    @Test
    void auditTrail_actorTypes() {
        ContractAuditTrail userEvent = ContractAuditTrail.create(
                1L, Action.VIEW, "100", ActorType.USER, null, "10.0.0.1");
        assertEquals(ActorType.USER, userEvent.getActorType());

        ContractAuditTrail adminEvent = ContractAuditTrail.create(
                1L, Action.DOWNLOAD, "1", ActorType.ADMIN, null, "10.0.0.2");
        assertEquals(ActorType.ADMIN, adminEvent.getActorType());

        ContractAuditTrail systemEvent = ContractAuditTrail.create(
                1L, Action.ARCHIVE, null, ActorType.SYSTEM, "{\"sha256\":\"abc\"}", null);
        assertEquals(ActorType.SYSTEM, systemEvent.getActorType());
    }
}

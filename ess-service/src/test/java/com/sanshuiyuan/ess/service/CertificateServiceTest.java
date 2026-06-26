package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanshuiyuan.ess.config.EssProperties;
import com.sanshuiyuan.ess.config.OssProperties;
import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.CertificateStatus;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import com.sanshuiyuan.ess.infra.client.EssApiClient;
import com.sanshuiyuan.ess.infra.client.OssStorageClient;
import com.sanshuiyuan.ess.infra.client.TencentCloudStorageClient;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock private ContractRepository contractRepository;
    @Mock private EssApiClient essApiClient;
    @Mock private AuditTrailService auditTrailService;
    @Mock private OssStorageClient ossStorageClient;
    @Mock private TencentCloudStorageClient tencentCloudStorageClient;
    @Mock private Contract contract;
    private final ObjectMapper om = new ObjectMapper();
    private CertificateService service;

    @BeforeEach
    void setUp() {
        EssProperties props = new EssProperties("sid", "skey", "op-001", "corp-001",
                null, null, "ap-guangzhou", "ess.tencentcloudapi.com", 5000, 15000, 3, true, "3");
        OssProperties oss = new OssProperties(null, null, null, null, "contracts/");
        service = new CertificateService(contractRepository, essApiClient, props, auditTrailService,
                ossStorageClient, tencentCloudStorageClient, oss);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(contract.getStatus()).thenReturn(ContractStatus.ARCHIVED);
        when(contract.getContractNo()).thenReturn("CT-x");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void step1_noReportId_submitsCreateFlowEvidenceReport() {
        when(contract.getCertificateStatus()).thenReturn(CertificateStatus.PENDING);
        when(contract.getEvidenceReportId()).thenReturn(null);
        when(contract.getEssFlowId()).thenReturn("flow-1");
        ObjectNode resp = om.createObjectNode();
        resp.put("ReportId", "report-123");
        when(essApiClient.invoke(eq("CreateFlowEvidenceReport"), any())).thenReturn(resp);

        var result = service.certifyContract(1L);

        ArgumentCaptor<TreeMap> cap = ArgumentCaptor.forClass(TreeMap.class);
        verify(essApiClient).invoke(eq("CreateFlowEvidenceReport"), cap.capture());
        TreeMap params = cap.getValue();
        assertEquals("flow-1", params.get("FlowId"));
        assertEquals(0, params.get("ReportType"));
        assertTrue(params.containsKey("Operator"), "应带 Operator");
        verify(contract).markCertifying();
        verify(contract).setEvidenceReportId("report-123"); // 持久化 ReportId
        assertEquals("APPLYING", result.status());
    }

    @Test
    void step2_success_archivesReportToDurableStorageThenCompletes() {
        when(contract.getId()).thenReturn(1L);
        when(contract.getCertificateStatus()).thenReturn(CertificateStatus.APPLYING);
        when(contract.getEvidenceReportId()).thenReturn("report-123");
        ObjectNode resp = om.createObjectNode();
        resp.put("Status", "EvidenceStatusSuccess");
        resp.put("ReportUrl", "https://ess/short-lived.pdf"); // 短效链接
        when(essApiClient.invoke(eq("DescribeFlowEvidenceReport"), any())).thenReturn(resp);

        CertificateService spy = spy(service);
        doReturn("PDFDATA".getBytes()).when(spy).downloadBytes("https://ess/short-lived.pdf");
        String durable = "https://oss.sanshuiyuan.com/contracts/CT-x-evidence-report.pdf";
        when(ossStorageClient.upload(anyString(), any(byte[].class), anyString())).thenReturn(durable);

        var result = spy.certifyContract(1L);

        assertTrue(result.success());
        verify(tencentCloudStorageClient).upload(anyString(), any(byte[].class), anyString());
        // 短效 ReportUrl 不入库，存的是持久 OSS URL。
        verify(contract).completeCertificate("report-123", durable);
    }

    @Test
    void step2_executing_staysApplying() {
        when(contract.getCertificateStatus()).thenReturn(CertificateStatus.APPLYING);
        when(contract.getEvidenceReportId()).thenReturn("report-123");
        ObjectNode resp = om.createObjectNode();
        resp.put("Status", "EvidenceStatusExecuting");
        when(essApiClient.invoke(eq("DescribeFlowEvidenceReport"), any())).thenReturn(resp);

        var result = service.certifyContract(1L);

        assertEquals("APPLYING", result.status());
        verify(contract, never()).completeCertificate(any(), any());
        verify(contract, never()).markCertificateFailed();
    }

    @Test
    void step2_failed_clearsReportIdAndMarksFailed_withoutRollback() {
        when(contract.getId()).thenReturn(1L); // 失败分支写审计用 getId
        when(contract.getCertificateStatus()).thenReturn(CertificateStatus.APPLYING);
        when(contract.getEvidenceReportId()).thenReturn("report-123");
        ObjectNode resp = om.createObjectNode();
        resp.put("Status", "EvidenceStatusFailed");
        when(essApiClient.invoke(eq("DescribeFlowEvidenceReport"), any())).thenReturn(resp);

        // 不抛异常（否则 @Transactional 回滚清理）：正常返回 FAILED 并落库。
        var result = service.certifyContract(1L);

        assertEquals("FAILED", result.status());
        verify(contract).setEvidenceReportId(null); // 清掉失败报告ID以便重提
        verify(contract).markCertificateFailed();
        verify(contractRepository).save(contract);  // 清理已落库（事务可提交）
    }
}

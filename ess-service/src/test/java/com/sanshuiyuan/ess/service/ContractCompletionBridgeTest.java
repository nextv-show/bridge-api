package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.EssFlowRecord;
import com.sanshuiyuan.ess.domain.FlowStatus;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import com.sanshuiyuan.ess.infra.repository.EssFlowRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractCompletionBridgeTest {

    @Mock private ContractRepository contractRepository;
    @Mock private EssFlowRecordRepository flowRecordRepository;
    @Mock private ContractSigningService signingService;
    @Mock private EssDocumentService documentService;
    @Mock private ObjectProvider<ContractSigningService> signingProvider;
    @Mock private ObjectProvider<EssDocumentService> documentProvider;

    private ContractCompletionBridge bridge;

    @BeforeEach
    void setUp() {
        lenient().when(signingProvider.getObject()).thenReturn(signingService);
        lenient().when(documentProvider.getObject()).thenReturn(documentService);
        bridge = new ContractCompletionBridge(
                contractRepository, flowRecordRepository, signingProvider, documentProvider);
    }

    private Contract signingContract() {
        Contract c = Contract.createDraft("CT-X", 1L, 10L, "ORD-1", "SN-1");
        c.markGenerated("{}", "[{\"userName\":\"张三\"}]");
        c.startSigning("flow-1");
        return c;
    }

    private EssFlowRecord flowRecordWithStatus(FlowStatus status) {
        EssFlowRecord r = EssFlowRecord.create("CT-X", "[{}]");
        r.assignFlowId("flow-1");
        if (status == FlowStatus.SIGNING) r.startSigning();
        if (status == FlowStatus.COMPLETED) r.complete("{}");
        if (status == FlowStatus.CANCELLED) r.cancel();
        if (status == FlowStatus.REJECTED) r.reject("{}");
        if (status == FlowStatus.ERROR) r.markError("{}");
        return r;
    }

    @Test
    void bridgeToSigned_flowRecordCompleted_promotesAndCallsSigningService() {
        Contract c = signingContract();
        when(contractRepository.findByContractNo("CT-X")).thenReturn(Optional.of(c));
        when(flowRecordRepository.findByContractId("CT-X"))
                .thenReturn(Optional.of(flowRecordWithStatus(FlowStatus.COMPLETED)));
        when(documentService.getFileUrls("CT-X")).thenReturn(List.of("https://ess/file.pdf"));

        boolean result = bridge.bridgeToSigned("CT-X", null);

        assertTrue(result);
        verify(signingService).completeSigning(c.getId(), "https://ess/file.pdf", "");
    }

    @Test
    void bridgeToSigned_flowRecordNotCompleted_neverPromotes() {
        // 关键防御：FlowRecord 仍 CREATED/SIGNING 时绝不能推进 Contract → SIGNED
        Contract c = signingContract();
        when(contractRepository.findByContractNo("CT-X")).thenReturn(Optional.of(c));
        when(flowRecordRepository.findByContractId("CT-X"))
                .thenReturn(Optional.of(flowRecordWithStatus(FlowStatus.CREATED)));

        boolean result = bridge.bridgeToSigned("CT-X", null);

        assertFalse(result);
        verifyNoInteractions(signingService);
        verifyNoInteractions(documentService); // 早退后甚至不去拉 PDF
    }

    @Test
    void bridgeToSigned_flowRecordSigning_neverPromotes() {
        Contract c = signingContract();
        when(contractRepository.findByContractNo("CT-X")).thenReturn(Optional.of(c));
        when(flowRecordRepository.findByContractId("CT-X"))
                .thenReturn(Optional.of(flowRecordWithStatus(FlowStatus.SIGNING)));

        assertFalse(bridge.bridgeToSigned("CT-X", null));
        verifyNoInteractions(signingService);
    }

    @Test
    void bridgeToSigned_flowRecordRejected_neverPromotes() {
        Contract c = signingContract();
        when(contractRepository.findByContractNo("CT-X")).thenReturn(Optional.of(c));
        when(flowRecordRepository.findByContractId("CT-X"))
                .thenReturn(Optional.of(flowRecordWithStatus(FlowStatus.REJECTED)));

        assertFalse(bridge.bridgeToSigned("CT-X", null));
        verifyNoInteractions(signingService);
    }

    @Test
    void bridgeToSigned_flowRecordMissing_returnsFalse() {
        Contract c = signingContract();
        when(contractRepository.findByContractNo("CT-X")).thenReturn(Optional.of(c));
        when(flowRecordRepository.findByContractId("CT-X")).thenReturn(Optional.empty());

        assertFalse(bridge.bridgeToSigned("CT-X", null));
        verifyNoInteractions(signingService);
    }

    @Test
    void bridgeToSigned_usesHashHintWhenProvided() {
        Contract c = signingContract();
        when(contractRepository.findByContractNo("CT-X")).thenReturn(Optional.of(c));
        when(flowRecordRepository.findByContractId("CT-X"))
                .thenReturn(Optional.of(flowRecordWithStatus(FlowStatus.COMPLETED)));
        when(documentService.getFileUrls("CT-X")).thenReturn(List.of("https://ess/file.pdf"));

        bridge.bridgeToSigned("CT-X", "sha256hint");

        verify(signingService).completeSigning(c.getId(), "https://ess/file.pdf", "sha256hint");
    }

    @Test
    void bridgeToSigned_pdfUrlFetchFails_stillPromotesWithNullUrl() {
        // ESS DescribeFileUrls 抛异常（例如尚未生成 PDF）也必须继续推进；归档会重试拉取。
        // 同时验证：异常不污染调用方事务（lift out of @Transactional 的设计）。
        Contract c = signingContract();
        when(contractRepository.findByContractNo("CT-X")).thenReturn(Optional.of(c));
        when(flowRecordRepository.findByContractId("CT-X"))
                .thenReturn(Optional.of(flowRecordWithStatus(FlowStatus.COMPLETED)));
        when(documentService.getFileUrls("CT-X")).thenThrow(new RuntimeException("ESS down"));

        boolean result = bridge.bridgeToSigned("CT-X", null);

        assertTrue(result);
        verify(signingService).completeSigning(eq(c.getId()), isNull(), eq(""));
    }

    @Test
    void bridgeToSigned_nonSigningContract_isNoop() {
        Contract c = signingContract();
        c.completeSigning("https://x", "h"); // 已经 SIGNED
        when(contractRepository.findByContractNo("CT-X")).thenReturn(Optional.of(c));

        boolean result = bridge.bridgeToSigned("CT-X", null);

        assertFalse(result);
        verifyNoInteractions(signingService);
        verifyNoInteractions(flowRecordRepository); // 早退后不查 FlowRecord
    }

    @Test
    void bridgeToSigned_contractNotFound_returnsFalseAndSwallows() {
        when(contractRepository.findByContractNo("CT-MISSING")).thenReturn(Optional.empty());

        boolean result = bridge.bridgeToSigned("CT-MISSING", null);

        assertFalse(result);
        verifyNoInteractions(signingService);
    }

    @Test
    void bridgeToSigned_signingServiceThrows_swallowsAndReturnsFalse() {
        Contract c = signingContract();
        when(contractRepository.findByContractNo("CT-X")).thenReturn(Optional.of(c));
        when(flowRecordRepository.findByContractId("CT-X"))
                .thenReturn(Optional.of(flowRecordWithStatus(FlowStatus.COMPLETED)));
        when(documentService.getFileUrls("CT-X")).thenReturn(List.of("https://ess/file.pdf"));
        doThrow(new RuntimeException("archive boom"))
                .when(signingService).completeSigning(any(), any(), any());

        boolean result = bridge.bridgeToSigned("CT-X", null);

        assertFalse(result, "桥接抛异常不应向上传播，下一次轮询/兜底会重试");
    }
}

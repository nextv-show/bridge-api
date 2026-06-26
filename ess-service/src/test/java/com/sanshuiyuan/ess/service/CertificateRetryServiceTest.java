package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateRetryServiceTest {

    @Mock private ContractRepository contractRepository;
    @Mock private CertificateService certificateService;

    @Test
    void disabled_doesNotScanOrCallCertify() {
        // 出证关闭：不扫描、不调用付费出证 API。
        new CertificateRetryService(contractRepository, certificateService, false).retryCertification();
        verifyNoInteractions(contractRepository, certificateService);
    }

    @Test
    void enabled_scansPendingContracts() {
        when(contractRepository.findByStatusAndCertificateStatusIn(any(), any())).thenReturn(List.<Contract>of());
        new CertificateRetryService(contractRepository, certificateService, true).retryCertification();
        verify(contractRepository).findByStatusAndCertificateStatusIn(any(), any());
    }
}

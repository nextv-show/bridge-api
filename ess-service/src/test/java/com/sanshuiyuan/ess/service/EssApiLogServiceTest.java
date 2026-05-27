package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.EssApiLog;
import com.sanshuiyuan.ess.infra.repository.EssApiLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EssApiLogServiceTest {

    @Mock
    private EssApiLogRepository apiLogRepository;

    private EssApiLogService service;

    @BeforeEach
    void setUp() {
        service = new EssApiLogService(apiLogRepository);
    }

    @Test
    void recordSuccess_shouldSaveLogWithoutError() {
        // Arrange
        when(apiLogRepository.save(any(EssApiLog.class))).thenAnswer(inv -> {
            EssApiLog log = inv.getArgument(0);
            return log;
        });

        // Act
        EssApiLog result = service.recordSuccess("CreateFlow", "{\"key\":\"val\"}",
                "{\"Response\":{}}", 200, 150);

        // Assert
        assertNotNull(result);
        assertEquals("CreateFlow", result.getApiAction());
        assertEquals(200, result.getStatusCode());
        assertEquals(150, result.getDurationMs());
        assertNull(result.getErrorMessage());

        ArgumentCaptor<EssApiLog> captor = ArgumentCaptor.forClass(EssApiLog.class);
        verify(apiLogRepository).save(captor.capture());
        EssApiLog saved = captor.getValue();
        assertEquals("CreateFlow", saved.getApiAction());
    }

    @Test
    void recordFailure_shouldSaveLogWithError() {
        // Arrange
        when(apiLogRepository.save(any(EssApiLog.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        EssApiLog result = service.recordFailure("StartFlow", "{}", null, 500,
                3000, "Connection refused");

        // Assert
        assertNotNull(result);
        assertEquals("StartFlow", result.getApiAction());
        assertEquals(500, result.getStatusCode());
        assertEquals("Connection refused", result.getErrorMessage());

        verify(apiLogRepository).save(any(EssApiLog.class));
    }

    @Test
    void recordSuccessAsync_shouldDelegateToRecordSuccess() {
        when(apiLogRepository.save(any(EssApiLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordSuccessAsync("DescribeFlowStatus", "{}", "{\"Response\":{}}", 200, 50);

        verify(apiLogRepository, timeout(1000)).save(any(EssApiLog.class));
    }
}

package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.domain.KycRecord;
import com.sanshuiyuan.admin.infra.repository.KycRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KycReviewServiceTest {

    @Mock private KycRecordRepository kycRepo;
    @Mock private AuditLogService auditLog;
    private KycReviewService service;

    @BeforeEach
    void setUp() {
        service = new KycReviewService(kycRepo, auditLog);
    }

    private KycRecord createPendingRecord(Long id) {
        KycRecord r = new KycRecord();
        setField(r, "id", id);
        setField(r, "openid", "test_openid");
        setField(r, "status", KycRecord.Status.PENDING);
        setField(r, "createdAt", LocalDateTime.now());
        return r;
    }

    private KycRecord createReviewedRecord(Long id, KycRecord.Status status) {
        KycRecord r = createPendingRecord(id);
        setField(r, "status", status);
        setField(r, "verifiedAt", LocalDateTime.now());
        return r;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void approve_success() {
        KycRecord record = createPendingRecord(1L);
        when(kycRepo.findById(1L)).thenReturn(Optional.of(record));

        service.approve(100L, 1L);

        assertEquals(KycRecord.Status.PASS, record.getStatus());
        assertNotNull(record.getVerifiedAt());
        verify(kycRepo).save(record);
        verify(auditLog).log(100L, "KYC_APPROVE", "kyc_record", "1", null);
    }

    @Test
    void reject_success() {
        KycRecord record = createPendingRecord(1L);
        when(kycRepo.findById(1L)).thenReturn(Optional.of(record));

        service.reject(100L, 1L, "证件照模糊");

        assertEquals(KycRecord.Status.REJECT, record.getStatus());
        assertNotNull(record.getVerifiedAt());
        assertEquals("证件照模糊", record.getRejectReason());
        verify(kycRepo).save(record);
        verify(auditLog).log(100L, "KYC_REJECT", "kyc_record", "1", "{\"reason\":\"证件照模糊\"}");
    }

    @Test
    void reject_blankReason_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.reject(100L, 1L, "  "));
        verify(kycRepo, never()).findById(anyLong());
        verify(kycRepo, never()).save(any());
    }

    @Test
    void approve_alreadyReviewed_throwsIllegalState() {
        KycRecord record = createReviewedRecord(1L, KycRecord.Status.PASS);
        when(kycRepo.findById(1L)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class, () -> service.approve(100L, 1L));

        verify(kycRepo, never()).save(any());
        verify(auditLog, never()).log(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void reject_alreadyReviewed_throwsIllegalState() {
        KycRecord record = createReviewedRecord(1L, KycRecord.Status.REJECT);
        when(kycRepo.findById(1L)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class, () -> service.reject(100L, 1L, "证件信息不符"));

        verify(kycRepo, never()).save(any());
    }

    @Test
    void approve_idempotent_rejectAlreadyDone() {
        // Approve on an already-rejected record should fail
        KycRecord record = createReviewedRecord(1L, KycRecord.Status.REJECT);
        when(kycRepo.findById(1L)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class, () -> service.approve(100L, 1L));

        // Status should remain REJECT, unchanged
        assertEquals(KycRecord.Status.REJECT, record.getStatus());
    }

    @Test
    void approve_recordNotFound_throwsIllegalArgument() {
        when(kycRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.approve(100L, 999L));
    }

    @Test
    void reject_recordNotFound_throwsIllegalArgument() {
        when(kycRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.reject(100L, 999L, "证件信息不符"));
    }
}

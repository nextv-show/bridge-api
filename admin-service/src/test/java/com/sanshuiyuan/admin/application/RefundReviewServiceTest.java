package com.sanshuiyuan.admin.application;

import com.sanshuiyuan.admin.domain.AdminUser;
import com.sanshuiyuan.admin.domain.RefundRecord;
import com.sanshuiyuan.admin.infra.repository.AdminUserRepository;
import com.sanshuiyuan.admin.infra.repository.RefundRecordRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundReviewServiceTest {

    @Mock private RefundRecordRepository refundRepo;
    @Mock private AdminUserRepository adminUserRepo;
    @Mock private AuditLogService auditLog;
    private RefundReviewService service;

    @BeforeEach
    void setUp() {
        service = new RefundReviewService(refundRepo, adminUserRepo, auditLog);
    }

    /** 创建一条 PENDING 状态的退款记录 */
    private RefundRecord createPendingRecord(Long id) {
        RefundRecord r = new RefundRecord();
        setField(r, "id", id);
        setField(r, "orderId", 100L);
        setField(r, "userId", 200L);
        setField(r, "refundNo", "RF-20260525-0001");
        setField(r, "refundType", RefundRecord.RefundType.FULL);
        setField(r, "status", RefundRecord.Status.PENDING);
        setField(r, "reasonCat", "REMORSE");
        setField(r, "orderAmountCents", 10000L);
        setField(r, "paidAmountCents", 10000L);
        setField(r, "refundAmountCents", 10000L);
        setField(r, "incomeDeductedCents", 0L);
        setField(r, "feeCents", 0L);
        setField(r, "riskLevel", "low");
        setField(r, "kycPassed", true);
        setField(r, "skuQty", 1);
        setField(r, "submittedAt", LocalDateTime.now());
        setField(r, "createdAt", LocalDateTime.now());
        setField(r, "updatedAt", LocalDateTime.now());
        return r;
    }

    /** 创建一条已完成审核的退款记录 */
    private RefundRecord createReviewedRecord(Long id, RefundRecord.Status status) {
        RefundRecord r = createPendingRecord(id);
        setField(r, "status", status);
        setField(r, "operatorId", 99L);
        setField(r, "resolvedAt", LocalDateTime.now());
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

    private AdminUser createAdmin(Long id, String username) {
        AdminUser admin = new AdminUser();
        try {
            Field f = AdminUser.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(admin, id);
            f = AdminUser.class.getDeclaredField("username");
            f.setAccessible(true);
            f.set(admin, username);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return admin;
    }

    /* ========== approve 测试 ========== */

    @Test
    void approve_success() {
        RefundRecord record = createPendingRecord(1L);
        when(refundRepo.findById(1L)).thenReturn(Optional.of(record));
        when(adminUserRepo.findById(100L)).thenReturn(Optional.of(createAdmin(100L, "admin1")));

        service.approve(100L, 1L, null);

        assertEquals(RefundRecord.Status.APPROVED, record.getStatus());
        assertEquals(100L, record.getOperatorId());
        assertEquals("admin1", record.getOperatorName());
        // 未指定 actualRefundCents 时默认使用 refundAmountCents
        assertEquals(10000L, record.getActualRefundCents());
        assertNotNull(record.getResolvedAt());
        verify(refundRepo).save(record);
        verify(auditLog).log(eq(100L), eq("REFUND_APPROVE"), eq("refund_record"), eq("1"), any());
    }

    @Test
    void approve_withCustomActualRefundCents() {
        RefundRecord record = createPendingRecord(1L);
        when(refundRepo.findById(1L)).thenReturn(Optional.of(record));
        when(adminUserRepo.findById(100L)).thenReturn(Optional.of(createAdmin(100L, "admin1")));

        service.approve(100L, 1L, 8000L);

        assertEquals(RefundRecord.Status.APPROVED, record.getStatus());
        assertEquals(8000L, record.getActualRefundCents());
        verify(refundRepo).save(record);
    }

    @Test
    void approve_recordNotFound_throwsIllegalArgument() {
        when(refundRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.approve(100L, 999L, null));
        verify(refundRepo, never()).save(any());
        verify(auditLog, never()).log(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void approve_alreadyApproved_throwsIllegalState() {
        RefundRecord record = createReviewedRecord(1L, RefundRecord.Status.APPROVED);
        when(refundRepo.findById(1L)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class, () -> service.approve(100L, 1L, null));
        // 状态保持不变
        assertEquals(RefundRecord.Status.APPROVED, record.getStatus());
        verify(refundRepo, never()).save(any());
    }

    @Test
    void approve_alreadyRejected_throwsIllegalState() {
        RefundRecord record = createReviewedRecord(1L, RefundRecord.Status.REJECTED);
        when(refundRepo.findById(1L)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class, () -> service.approve(100L, 1L, null));
        assertEquals(RefundRecord.Status.REJECTED, record.getStatus());
    }

    @Test
    void approve_alreadyRefunded_throwsIllegalState() {
        RefundRecord record = createReviewedRecord(1L, RefundRecord.Status.REFUNDED);
        when(refundRepo.findById(1L)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class, () -> service.approve(100L, 1L, null));
    }

    /* ========== reject 测试 ========== */

    @Test
    void reject_success() {
        RefundRecord record = createPendingRecord(1L);
        when(refundRepo.findById(1L)).thenReturn(Optional.of(record));
        when(adminUserRepo.findById(100L)).thenReturn(Optional.of(createAdmin(100L, "admin1")));

        service.reject(100L, 1L, "材料不完整");

        assertEquals(RefundRecord.Status.REJECTED, record.getStatus());
        assertEquals(100L, record.getOperatorId());
        assertEquals("admin1", record.getOperatorName());
        assertEquals("材料不完整", record.getRejectReason());
        assertNotNull(record.getResolvedAt());
        verify(refundRepo).save(record);
        verify(auditLog).log(eq(100L), eq("REFUND_REJECT"), eq("refund_record"), eq("1"), any());
    }

    @Test
    void reject_recordNotFound_throwsIllegalArgument() {
        when(refundRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.reject(100L, 999L, "理由"));
        verify(refundRepo, never()).save(any());
    }

    @Test
    void reject_alreadyRejected_throwsIllegalState() {
        RefundRecord record = createReviewedRecord(1L, RefundRecord.Status.REJECTED);
        when(refundRepo.findById(1L)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class, () -> service.reject(100L, 1L, "再次驳回"));
        assertEquals(RefundRecord.Status.REJECTED, record.getStatus());
        verify(refundRepo, never()).save(any());
    }

    @Test
    void reject_alreadyApproved_throwsIllegalState() {
        RefundRecord record = createReviewedRecord(1L, RefundRecord.Status.APPROVED);
        when(refundRepo.findById(1L)).thenReturn(Optional.of(record));

        assertThrows(IllegalStateException.class, () -> service.reject(100L, 1L, "尝试驳回已通过的"));
        assertEquals(RefundRecord.Status.APPROVED, record.getStatus());
    }
}

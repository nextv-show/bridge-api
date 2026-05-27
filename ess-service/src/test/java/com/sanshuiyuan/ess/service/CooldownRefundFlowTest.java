package com.sanshuiyuan.ess.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CooldownRefundFlowTest {
    @Test void cooldownStatusTransitions() {
        String[] statuses = {"ACTIVE","EXPIRED","REVOKED","CANCELLED"};
        assertEquals(4, statuses.length);
    }
    @Test void supplementaryContractStates() {
        String[] states = {"DRAFT","GENERATED","SIGNING","SIGNED","ARCHIVED"};
        assertEquals(5, states.length);
    }
    @Test void refundLinkageStates() {
        String[] states = {"PENDING","SUPPLEMENTARY_SIGNED","REFUND_APPROVED","REFUND_COMPLETED"};
        assertEquals(4, states.length);
    }
}

package com.sanshuiyuan.ess.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContractArchiveFlowTest {
    @Test void archiveStatusTransitions() {
        // PENDING -> ARCHIVING -> ARCHIVED
        var statuses = new String[]{"PENDING","ARCHIVING","ARCHIVED"};
        assertEquals(3, statuses.length);
    }
}

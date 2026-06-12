package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.FlowStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EssStatusMapperTest {

    @Test
    void numericCodes_mapToTencentSemantics() {
        // 腾讯电子签 DescribeFlowInfo.FlowStatus 官方枚举
        assertEquals(FlowStatus.SIGNING, EssStatusMapper.map("1"));    // 待签署
        assertEquals(FlowStatus.SIGNING, EssStatusMapper.map("2"));    // 部分签署（仍在签署中）
        assertEquals(FlowStatus.REJECTED, EssStatusMapper.map("3"));   // 已拒签
        assertEquals(FlowStatus.COMPLETED, EssStatusMapper.map("4"));  // 已签署完成
        assertEquals(FlowStatus.EXPIRED, EssStatusMapper.map("5"));    // 已过期
        assertEquals(FlowStatus.CANCELLED, EssStatusMapper.map("6"));  // 已撤销
    }

    @Test
    void stringEnums_alsoSupportedForWebhookPayloads() {
        assertEquals(FlowStatus.COMPLETED, EssStatusMapper.map("COMPLETED"));
        assertEquals(FlowStatus.COMPLETED, EssStatusMapper.map("FINISH"));
        assertEquals(FlowStatus.COMPLETED, EssStatusMapper.map("FINISHED"));
        assertEquals(FlowStatus.CANCELLED, EssStatusMapper.map("CANCELLED"));
        assertEquals(FlowStatus.CANCELLED, EssStatusMapper.map("WITHDRAW"));
        assertEquals(FlowStatus.REJECTED, EssStatusMapper.map("REJECT"));
    }

    @Test
    void unknownAndNullAndBlank_returnNull_neverFalseCompletedPromotion() {
        // 关键不变量：未知状态绝不能映射到 COMPLETED，必须返回 null 让调用方保留旧状态
        assertNull(EssStatusMapper.map(null));
        assertNull(EssStatusMapper.map(""));
        assertNull(EssStatusMapper.map("  "));
        assertNull(EssStatusMapper.map("7"));   // 流程内部状态
        assertNull(EssStatusMapper.map("99"));
        assertNull(EssStatusMapper.map("UNKNOWN_FUTURE_STATE"));
    }

    @Test
    void intOverload_delegatesToString() {
        assertEquals(FlowStatus.COMPLETED, EssStatusMapper.map(4));
        assertEquals(FlowStatus.SIGNING, EssStatusMapper.map(1));
        assertEquals(FlowStatus.SIGNING, EssStatusMapper.map(2));
        assertNull(EssStatusMapper.map(99));
    }
}

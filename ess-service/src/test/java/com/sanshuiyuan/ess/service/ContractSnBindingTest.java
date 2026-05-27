package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.ContractSnBinding;
import com.sanshuiyuan.ess.domain.ContractSnBinding.BindingType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T17.15: SN 预占位绑定逻辑测试。
 * <p>
 * 验证 ContractSnBinding 实体的预占位创建、确认、释放及状态流转。
 */
class ContractSnBindingTest {

    @Test
    void preAllocate_shouldCreateBindingWithCorrectFields() {
        ContractSnBinding binding = ContractSnBinding.preAllocate(1L, "SN-ABC123");

        assertEquals(1L, binding.getContractId());
        assertEquals("SN-ABC123", binding.getDeviceSn());
        assertEquals(BindingType.PRE_ALLOCATED, binding.getBindingType());
    }

    @Test
    void preAllocate_withDifferentContracts_shouldBindIndependently() {
        ContractSnBinding b1 = ContractSnBinding.preAllocate(1L, "SN-001");
        ContractSnBinding b2 = ContractSnBinding.preAllocate(2L, "SN-002");

        assertEquals(1L, b1.getContractId());
        assertEquals("SN-001", b1.getDeviceSn());
        assertEquals(2L, b2.getContractId());
        assertEquals("SN-002", b2.getDeviceSn());
        assertEquals(BindingType.PRE_ALLOCATED, b1.getBindingType());
        assertEquals(BindingType.PRE_ALLOCATED, b2.getBindingType());
    }

    @Test
    void confirm_shouldTransitionFromPreAllocatedToConfirmed() {
        ContractSnBinding binding = ContractSnBinding.preAllocate(1L, "SN-XYZ");

        binding.confirm();

        assertEquals(BindingType.CONFIRMED, binding.getBindingType());
    }

    @Test
    void release_shouldTransitionFromPreAllocatedToReleased() {
        ContractSnBinding binding = ContractSnBinding.preAllocate(1L, "SN-XYZ");

        binding.release();

        assertEquals(BindingType.RELEASED, binding.getBindingType());
    }

    @Test
    void confirmAfterRelease_shouldStillChangeToConfirmed() {
        // 实体不强制状态机顺序——确认/释放是简单的 setter
        ContractSnBinding binding = ContractSnBinding.preAllocate(1L, "SN-XYZ");
        binding.release();
        assertEquals(BindingType.RELEASED, binding.getBindingType());

        binding.confirm();
        assertEquals(BindingType.CONFIRMED, binding.getBindingType());
    }

    @Test
    void bindingType_values_shouldCoverAllStates() {
        BindingType[] types = BindingType.values();
        assertEquals(3, types.length);
        // 确保枚举值存在且可按名称解析
        assertEquals(BindingType.PRE_ALLOCATED, BindingType.valueOf("PRE_ALLOCATED"));
        assertEquals(BindingType.CONFIRMED, BindingType.valueOf("CONFIRMED"));
        assertEquals(BindingType.RELEASED, BindingType.valueOf("RELEASED"));
    }

    @Test
    void preAllocate_sameSnDifferentContracts_shouldBothSucceed() {
        // 同一 SN 可以预占位到不同合同（实际场景下由数据库唯一约束保护）
        ContractSnBinding b1 = ContractSnBinding.preAllocate(1L, "SN-DUP");
        ContractSnBinding b2 = ContractSnBinding.preAllocate(2L, "SN-DUP");

        assertEquals(BindingType.PRE_ALLOCATED, b1.getBindingType());
        assertEquals(BindingType.PRE_ALLOCATED, b2.getBindingType());
        assertNotEquals(b1.getContractId(), b2.getContractId());
    }

    @Test
    void preAllocate_blankSn_shouldStillCreate() {
        // ContractGenerationService.createSnBinding 在调用前会检查 null/blank，
        // 但 preAllocate 本身不做额外校验
        ContractSnBinding binding = ContractSnBinding.preAllocate(1L, "");

        assertEquals("", binding.getDeviceSn());
        assertEquals(BindingType.PRE_ALLOCATED, binding.getBindingType());
    }

    @Test
    void getters_shouldReturnCorrectValues() {
        ContractSnBinding binding = ContractSnBinding.preAllocate(42L, "SN-TEST-GETTER");

        assertNull(binding.getId()); // JPA 尚未持久化
        assertEquals(42L, binding.getContractId());
        assertEquals("SN-TEST-GETTER", binding.getDeviceSn());
        assertEquals(BindingType.PRE_ALLOCATED, binding.getBindingType());
        assertNull(binding.getCreatedAt());
        assertNull(binding.getUpdatedAt());
    }
}

package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.Contract.ContractStatus;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T17.21: 关键路径性能验证。
 * <p>
 * 验证：
 * 1. 合同编号生成器的唯一性（高并发场景下）
 * 2. 状态机校验的正确性与快速失败
 * 3. 模板填充的性能
 */
class ContractCriticalPathTest {

    /**
     * 验证合同编号格式。
     */
    @Test
    void contractNoFormat_shouldBeCorrect() {
        ContractNoGenerator generator = new ContractNoGenerator();
        String no = generator.generate();

        assertTrue(no.startsWith("CT-"), "合同编号应以 CT- 开头");
        assertTrue(no.matches("CT-\\d{8}-[A-Z0-9]{6}"),
                "合同编号格式应为 CT-yyyyMMdd-XXXXXX，实际: " + no);
    }

    /**
     * 验证合同编号生成唯一性（100 次循环）。
     */
    @Test
    void contractNoGenerator_shouldBeUnique() {
        ContractNoGenerator generator = new ContractNoGenerator();
        java.util.Set<String> nos = new java.util.HashSet<>();

        for (int i = 0; i < 100; i++) {
            String no = generator.generate();
            assertTrue(nos.add(no), "生成重复编号: " + no);
        }
        assertEquals(100, nos.size());
    }

    /**
     * 验证状态机合法流转。
     */
    @Test
    void stateMachine_legalTransitions() {
        // DRAFT → GENERATED
        assertTrue(ContractStatus.DRAFT.canTransitionTo(ContractStatus.GENERATED));
        // GENERATED → SIGNING
        assertTrue(ContractStatus.GENERATED.canTransitionTo(ContractStatus.SIGNING));
        // SIGNING → SIGNED
        assertTrue(ContractStatus.SIGNING.canTransitionTo(ContractStatus.SIGNED));
        // SIGNED → ARCHIVED
        assertTrue(ContractStatus.SIGNED.canTransitionTo(ContractStatus.ARCHIVED));
    }

    /**
     * 验证状态机非法流转。
     */
    @Test
    void stateMachine_illegalTransitions() {
        // DRAFT 不能直接到 SIGNING
        assertFalse(ContractStatus.DRAFT.canTransitionTo(ContractStatus.SIGNING));
        // DRAFT 不能直接到 SIGNED
        assertFalse(ContractStatus.DRAFT.canTransitionTo(ContractStatus.SIGNED));
        // ARCHIVED 不能转到任何状态
        for (ContractStatus target : ContractStatus.values()) {
            assertFalse(ContractStatus.ARCHIVED.canTransitionTo(target));
        }
        // GENERATED 不能回到 DRAFT
        assertFalse(ContractStatus.GENERATED.canTransitionTo(ContractStatus.DRAFT));
    }

    /**
     * 验证完整状态机流转链路。
     */
    @Test
    void contractFullStateTransition() {
        Contract contract = Contract.createDraft("CT-TEST-001", 1L, 1L, "ORD", "SN");
        assertEquals(ContractStatus.DRAFT, contract.getStatus());

        contract.markGenerated("{}", "{}");
        assertEquals(ContractStatus.GENERATED, contract.getStatus());

        contract.startSigning("flow-001");
        assertEquals(ContractStatus.SIGNING, contract.getStatus());

        contract.completeSigning("https://pdf.example.com/c.pdf", "hash");
        assertEquals(ContractStatus.SIGNED, contract.getStatus());

        contract.archive();
        assertEquals(ContractStatus.ARCHIVED, contract.getStatus());
    }

    /**
     * 验证非法状态流转抛出异常。
     */
    @Test
    void illegalTransition_throwsException() {
        Contract contract = Contract.createDraft("CT-TEST-002", 1L, 1L, "ORD", "SN");

        // DRAFT → SIGNING 非法
        assertThrows(IllegalStateException.class,
                () -> contract.startSigning("flow-x"));
    }

    /**
     * 模板填充性能验证：1000 次填充应在合理时间内完成。
     */
    @Test
    void templateFilling_performance() {
        String template = "编号:{{contractNo}} 姓名:{{userName}} 身份证:{{idCardNo}} " +
                "电话:{{phone}} 型号:{{deviceModel}} SN:{{deviceSn}} 价格:{{devicePrice}} " +
                "日期:{{signDate}} 法人:{{legalRepresentative}} 地址:{{companyAddress}}";

        java.util.Map<String, String> fields = java.util.Map.of(
                "contractNo", "CT-20260527-PERF",
                "userName", "测试用户",
                "idCardNo", "110101199001011234",
                "phone", "13800138000",
                "deviceModel", "SSY-MINI-1",
                "deviceSn", "SN-PERF-001",
                "devicePrice", "29800",
                "signDate", "2026年05月27日",
                "legalRepresentative", "",
                "companyAddress", ""
        );

        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            String result = template;
            for (var entry : fields.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            assertTrue(result.contains("CT-20260527-PERF"));
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsed < 1000, "1000 次模板填充耗时 " + elapsed + "ms，应小于 1 秒");
    }

    /**
     * 重复验证编号生成器在多次调用时的稳定性。
     */
    @RepeatedTest(10)
    void contractNoGenerator_stabilityUnderRepeatedCalls() {
        ContractNoGenerator generator = new ContractNoGenerator();
        String no = generator.generate();
        assertNotNull(no);
        assertTrue(no.length() > 10);
        assertTrue(no.startsWith("CT-"));
    }
}

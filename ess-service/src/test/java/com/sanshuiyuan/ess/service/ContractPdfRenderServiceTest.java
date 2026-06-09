package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.config.ContractPdfProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ContractPdfRenderServiceTest {

    private final ContractPdfRenderService service =
            new ContractPdfRenderService(new ContractPdfProperties("", "A4"));

    @Test
    void rendersMarkdownToValidPdf() {
        String markdown = """
                # 三水元智能水机设备管理与服务协议

                **协议编号：** CT-20260608-abc123

                ## 第一条 设备信息

                - 设备型号：SSY-100
                - 设备唯一出厂编码：SN-123456
                - 设备购机款：人民币 3980 元

                | 甲方 | 乙方 |
                | :--: | :--: |
                | 张三 | 天津源创智能科技有限公司 |
                | 电子签字：______ | 公章：______ |
                """;

        byte[] pdf = service.renderMarkdownToPdf(markdown, "测试合同");

        assertNotNull(pdf);
        assertTrue(pdf.length > 800, "PDF 应有实际内容");
        String header = new String(pdf, 0, 5, StandardCharsets.ISO_8859_1);
        assertEquals("%PDF-", header, "应为合法 PDF 头");
    }

    @Test
    void handlesEmptyMarkdownGracefully() {
        byte[] pdf = service.renderMarkdownToPdf("", "空合同");
        assertNotNull(pdf);
        String header = new String(pdf, 0, 5, StandardCharsets.ISO_8859_1);
        assertEquals("%PDF-", header);
    }
}

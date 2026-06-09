package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.config.ContractPdfProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.awt.Color;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次性样张生成（非 CI）。仅当环境变量 RENDER_SAMPLE=1 时运行：
 * <pre>RENDER_SAMPLE=1 ./gradlew :ess-service:test --tests "*ContractSampleRenderTest"</pre>
 * <p>
 * 用真实 V2 合同正文渲染 PDF，并在「电子签字」「公章」关键字处叠加红框，
 * 模拟腾讯电子签关键字定位（GenerateMode=KEYWORD, RelativeLocation=Right）控件的实际落位，
 * 供人工核对签名/签章位置是否合适。
 */
@EnabledIfEnvironmentVariable(named = "RENDER_SAMPLE", matches = "1")
class ContractSampleRenderTest {

    private static final String SRC_MD =
            "/Users/nextvshow/projects/sanshuiyuan/docs/business/01-现行文本/"
                    + "三水元智能水机设备管理与服务协议（三合一修订稿V2）.md";
    private static final String OUT_PLAIN = "/Users/nextvshow/projects/sanshuiyuan/合同样张.pdf";
    private static final String OUT_MARKED = "/Users/nextvshow/projects/sanshuiyuan/合同样张-签名落位.pdf";

    // 与 EssFileProperties 默认值一致
    private static final float SIGN_W = 120f;
    private static final float SIGN_H = 44f;
    private static final float OFFSET_X = 5f;
    // 乙方签章独立参数（默认 Below）
    private static final float SEAL_W = 100f;
    private static final float SEAL_H = 100f;
    private static final float SEAL_OFFSET_Y = 5f;

    @Test
    void renderRealContractSample() throws Exception {
        File mdFile = new File(SRC_MD);
        if (!mdFile.isFile()) {
            System.out.println("[sample] 源 markdown 不存在，跳过: " + SRC_MD);
            return;
        }

        String markdown = Files.readString(mdFile.toPath(), StandardCharsets.UTF_8);
        String filled = fill(markdown, sampleFields());

        // 可用 SAMPLE_FONT_PATH 指定字体（用于校验生产服务器的 CJK 字体文件）；留空则自动探测。
        String fontPath = System.getenv().getOrDefault("SAMPLE_FONT_PATH", "");
        ContractPdfRenderService renderer =
                new ContractPdfRenderService(new ContractPdfProperties(fontPath, "A4"));
        byte[] pdf = renderer.renderMarkdownToPdf(filled, "三水元设备合同样张");

        Files.write(Path.of(OUT_PLAIN), pdf);
        System.out.println("[sample] 已生成正文样张: " + OUT_PLAIN + " (" + pdf.length + " bytes)");

        byte[] marked = annotateSignAnchors(pdf);
        Files.write(Path.of(OUT_MARKED), marked);
        System.out.println("[sample] 已生成签名落位标注: " + OUT_MARKED + " (" + marked.length + " bytes)");
    }

    private Map<String, String> sampleFields() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("contractNo", "CT-20260608-A1B2C3");
        f.put("signDate", "2026年06月08日");
        f.put("userName", "张三");
        f.put("idCardNo", "120103199001011234");
        f.put("phone", "13800138000");
        f.put("deviceModel", "三水元 SSY-100 智能水机");
        f.put("deviceSn", "SSY100-2026-000123");
        f.put("devicePrice", "3980");
        f.put("legalRepresentative", "李四");
        f.put("companyAddress", "天津市津南区双港科技产业园 A 座 6 层");
        return f;
    }

    private String fill(String template, Map<String, String> fields) {
        String r = template;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            r = r.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return r;
    }

    /** 在 PDF 的「电子签字」「公章」关键字右侧叠加红框 + 英文标签，模拟控件落位。 */
    private byte[] annotateSignAnchors(byte[] pdf) {
        try (PDDocument doc = PDDocument.load(pdf)) {
            // 甲方签名：Right，120x44
            drawBoxAtKeyword(doc, "电子签字", "Party A e-signature (Right)",
                    SIGN_W, SIGN_H, "Right", OFFSET_X, 0f);
            // 乙方签章：Below，100x100（避免靠右栏 Right 溢出页面）
            drawBoxAtKeyword(doc, "公章", "Party B seal (Below)",
                    SEAL_W, SEAL_H, "Below", 0f, SEAL_OFFSET_Y);
            java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream();
            doc.save(os);
            return os.toByteArray();
        } catch (Exception e) {
            System.out.println("[sample] 标注失败（仅输出正文样张）: " + e.getMessage());
            return pdf;
        }
    }

    private void drawBoxAtKeyword(PDDocument doc, String keyword, String label,
                                   float boxW, float boxH, String placement,
                                   float offsetX, float offsetY) throws Exception {
        for (int pageNo = 1; pageNo <= doc.getNumberOfPages(); pageNo++) {
            List<TextPosition> all = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition text) {
                    super.processTextPosition(text);
                    all.add(text);
                }
            };
            stripper.setSortByPosition(true);
            stripper.setStartPage(pageNo);
            stripper.setEndPage(pageNo);
            stripper.getText(doc);

            StringBuilder sb = new StringBuilder();
            for (TextPosition t : all) sb.append(t.getUnicode());
            int idx = sb.indexOf(keyword);
            if (idx < 0 || idx + keyword.length() > all.size()) continue;

            List<TextPosition> hit = all.subList(idx, idx + keyword.length());
            float left = Float.MAX_VALUE, right = 0f, topFromTop = Float.MAX_VALUE, fontH = 0f;
            for (TextPosition t : hit) {
                left = Math.min(left, t.getXDirAdj());
                right = Math.max(right, t.getXDirAdj() + t.getWidthDirAdj());
                topFromTop = Math.min(topFromTop, t.getYDirAdj());
                fontH = Math.max(fontH, t.getHeightDir());
            }
            PDPage page = doc.getPage(pageNo - 1);
            float pageH = page.getMediaBox().getHeight();

            float boxLeftBl, boxBottomBl;
            if ("Below".equalsIgnoreCase(placement)) {
                // 盖在关键字正下方：左对齐关键字，box 顶部在文字基线下方一点
                boxLeftBl = left + offsetX;
                float boxTopFromTop = topFromTop + offsetY;
                boxBottomBl = pageH - boxTopFromTop - boxH;
            } else {
                // Right：放在关键字右侧，垂直居中于文字行
                float textCenterFromTop = topFromTop - fontH / 2f;
                boxLeftBl = right + offsetX;
                boxBottomBl = pageH - textCenterFromTop - boxH / 2f;
            }

            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.setStrokingColor(Color.RED);
                cs.setLineWidth(1.2f);
                cs.addRect(boxLeftBl, boxBottomBl, boxW, boxH);
                cs.stroke();
                cs.beginText();
                cs.setNonStrokingColor(Color.RED);
                cs.setFont(PDType1Font.HELVETICA, 6.5f);
                cs.newLineAtOffset(boxLeftBl + 2f, boxBottomBl + boxH + 2f);
                cs.showText(label);
                cs.endText();
            }
            System.out.printf("[sample] 关键字「%s」落位 page=%d placement=%s x=%.1f y=%.1f w=%.0f h=%.0f%n",
                    keyword, pageNo, placement, boxLeftBl, boxBottomBl, boxW, boxH);
            return; // 只标第一处
        }
        System.out.println("[sample] 未找到关键字: " + keyword);
    }
}

package com.sanshuiyuan.ess.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.sanshuiyuan.ess.config.ContractPdfProperties;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

/**
 * 合同正文 PDF 渲染服务（文件模式发起核心）。
 * <p>
 * 流程：已填好变量的 markdown → HTML(flexmark, 支持表格) → 清洗为 XHTML(jsoup)
 * → PDF(openhtmltopdf + pdfbox)。渲染出的 PDF 正文已不含任何 {@code {{变量}}} 占位符，
 * 之后只交给腾讯电子签放置签名/签章控件。
 */
@Service
public class ContractPdfRenderService {

    private static final Logger log = LoggerFactory.getLogger(ContractPdfRenderService.class);

    /** PDF 内注册的 CJK 字体族名；CSS font-family 与此一致 */
    private static final String CJK_FAMILY = "ContractCJK";

    /** 字体路径未配置时按序探测的常见系统 CJK 字体 */
    // 必须是【单文件 TrueType(.ttf，含 glyf 字形)】：openhtmltopdf/PDFBox 既无法加载 .ttc 字体集，
    // 也无法嵌入 CFF/OTF 字体——Noto Sans CJK 等 .otf 会在渲染时抛 NPE。故此处只列单文件 .ttf。
    private static final List<String> SYSTEM_FONT_CANDIDATES = List.of(
            // Anolis/CentOS/Alibaba Cloud Linux：dnf install google-droid-sans-fonts
            "/usr/share/fonts/google-droid/DroidSansFallback.ttf",
            // Debian/Ubuntu：apt install fonts-droid-fallback
            "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
            // macOS
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/Library/Fonts/Arial Unicode.ttf"
    );

    private final ContractPdfProperties properties;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public ContractPdfRenderService(ContractPdfProperties properties) {
        this.properties = properties;
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
        this.markdownParser = Parser.builder(options).build();
        this.htmlRenderer = HtmlRenderer.builder(options).build();
    }

    /**
     * 将合同 markdown 正文渲染为 PDF 字节。
     *
     * @param markdown 已替换完变量的合同正文（markdown）
     * @param title    文档标题（仅用于日志/PDF 元信息，可为 null）
     * @return PDF 二进制
     */
    public byte[] renderMarkdownToPdf(String markdown, String title) {
        String bodyHtml = htmlRenderer.render(markdownParser.parse(markdown == null ? "" : markdown));
        String xhtml = wrapHtml(bodyHtml);

        org.jsoup.nodes.Document jsoupDoc = Jsoup.parse(xhtml);
        jsoupDoc.outputSettings()
                .syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
                .charset(java.nio.charset.StandardCharsets.UTF_8);
        org.w3c.dom.Document w3cDoc = new W3CDom().fromJsoup(jsoupDoc);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFont(builder);
            builder.withW3cDocument(w3cDoc, "/");
            builder.toStream(os);
            builder.run();
            byte[] pdf = os.toByteArray();
            log.info("合同 PDF 渲染完成 [title={}, bytes={}]", title, pdf.length);
            return pdf;
        } catch (Exception e) {
            throw new IllegalStateException("合同 PDF 渲染失败: " + e.getMessage(), e);
        }
    }

    private void registerFont(PdfRendererBuilder builder) {
        File font = resolveFont();
        if (font != null) {
            try {
                builder.useFont(font, CJK_FAMILY);
                log.debug("合同 PDF 使用 CJK 字体 [{}]", font.getAbsolutePath());
                return;
            } catch (Exception e) {
                log.warn("注册 CJK 字体失败 [{}]: {}，PDF 中文可能显示为方块", font.getAbsolutePath(), e.getMessage());
            }
        }
        log.warn("未找到可用 CJK 字体，PDF 中文可能显示为方块；请配置 contract.pdf.font-path 指向 ttf/otf 字体文件");
    }

    private File resolveFont() {
        if (properties.fontPath() != null && !properties.fontPath().isBlank()) {
            File configured = new File(properties.fontPath());
            if (configured.isFile()) {
                return configured;
            }
            log.warn("配置的字体路径不存在: {}", properties.fontPath());
        }
        for (String candidate : SYSTEM_FONT_CANDIDATES) {
            File f = new File(candidate);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    private String wrapHtml(String body) {
        String size = (properties.pageSize() == null || properties.pageSize().isBlank())
                ? "A4" : properties.pageSize();
        return "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"zh-CN\"><head>"
                + "<meta charset=\"utf-8\"/><style>"
                + "@page{size:" + size + ";margin:2.2cm 1.8cm;}"
                + "body{font-family:'" + CJK_FAMILY + "',sans-serif;font-size:10.5pt;line-height:1.7;color:#000;}"
                + "h1{font-size:17pt;text-align:center;margin:0 0 8pt;}"
                + "h2{font-size:12.5pt;border-bottom:1px solid #888;padding-bottom:3pt;margin:14pt 0 6pt;}"
                + "h3{font-size:11pt;margin:10pt 0 4pt;}"
                + "p{margin:4pt 0;}ul,ol{margin:4pt 0 4pt 18pt;}"
                // 签署区表格不被分页截断：避免甲方签字与乙方签章被切到不同页，
                // 否则关键字定位的签名/签章控件会落在不同页。本合同正文仅末尾一张签署表，
                // 整表保持在同一页是期望行为。
                + "table{width:100%;border-collapse:collapse;margin:6pt 0;page-break-inside:avoid;}"
                + "tr{page-break-inside:avoid;}"
                + "td,th{border:1px solid #333;padding:5pt;font-size:10pt;}"
                + "hr{border:none;border-top:1px solid #bbb;margin:10pt 0;}"
                + "</style></head><body>" + body + "</body></html>";
    }
}

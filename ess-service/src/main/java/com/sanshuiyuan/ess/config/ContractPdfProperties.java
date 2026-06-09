package com.sanshuiyuan.ess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 合同 PDF 渲染配置（文件模式发起用）。
 * <p>
 * 渲染中文合同必须有 CJK 字体，否则正文会显示为方块（豆腐块）。
 * 优先使用 {@link #fontPath()} 指定的字体文件；留空时回退到运行环境的常见系统 CJK 字体。
 */
@ConfigurationProperties(prefix = "contract.pdf")
public record ContractPdfProperties(
        /** CJK 字体文件绝对路径（ttf/otf/ttc）；留空则自动探测系统字体 */
        String fontPath,

        /** 页面尺寸，对应 CSS @page size，默认 A4 */
        String pageSize
) {
    public ContractPdfProperties {
        if (fontPath == null) fontPath = "";
        if (pageSize == null || pageSize.isBlank()) pageSize = "A4";
    }
}

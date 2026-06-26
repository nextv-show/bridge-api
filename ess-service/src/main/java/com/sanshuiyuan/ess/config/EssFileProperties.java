package com.sanshuiyuan.ess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯电子签「文件模式发起」（CreateFlowByFiles）配置属性。
 * <p>
 * 与模板模式（{@link EssProperties#templateId()}）互斥：当 {@link #enabled()} 为 true 时，
 * 合同正文由后端自行渲染成 PDF（所有 {@code {{变量}}} 已替换），上传腾讯电子签后只声明
 * 签名/签章控件（通过关键字定位），平台不再持有任何业务变量。
 * <p>
 * 默认关闭，保证未配置时维持既有模板模式行为，可安全合并到生产再按需开启。
 */
@ConfigurationProperties(prefix = "ess.file")
public record EssFileProperties(
        /** 是否启用文件模式发起；false 时走既有模板模式 */
        Boolean enabled,

        /** 文件上传专用域名（与普通 API 域名不同）。联调环境：file.test.ess.tencent.cn */
        String endpoint,

        /** 甲方个人签名控件关键字（在渲染后的 PDF 中出现一次的锚点文字） */
        String signatureKeyword,

        /** 甲方签署日期控件关键字；留空表示不放 SIGN_DATE 控件（签署日期已由后端预填） */
        String dateKeyword,

        /** 是否为乙方（企业）追加签章控件；静默签需腾讯电子签账号侧授权印章，故默认关闭 */
        Boolean companySeal,

        /** 乙方企业签章控件关键字 */
        String sealKeyword,

        /** 乙方企业名称（作为企业签署方 OrganizationName） */
        String companyName,

        /** 甲方签名控件关键字定位相对方位：Right/Below/LowerRight/UpperRight/Middle */
        String relativeLocation,

        /** 甲方签名控件宽度（pt） */
        Double signWidth,

        /** 甲方签名控件高度（pt） */
        Double signHeight,

        /** 甲方签名控件横向偏移（pt，正数向右） */
        Double offsetX,

        /** 甲方签名控件纵向偏移（pt，正数向下） */
        Double offsetY,

        /** 乙方签章控件相对方位：印章靠近页边，默认 Below（盖在「公章」标签下方，避免 Right 溢出页面） */
        String sealRelativeLocation,

        /** 乙方签章控件宽度（pt），公章一般近方形 */
        Double sealWidth,

        /** 乙方签章控件高度（pt） */
        Double sealHeight,

        /** 乙方签章控件横向偏移（pt） */
        Double sealOffsetX,

        /** 乙方签章控件纵向偏移（pt） */
        Double sealOffsetY,

        /**
         * 乙方企业自动签（静默签）印章 ID（32 位）。
         * <p>本企业自动盖章（ApproverType=3）时，SIGN_SEAL 控件必须经 {@code ComponentValue} 指定
         * 当前经办人已授权的印章 Id（腾讯电子签控制台 → 印章 → 印章中心 → 印章ID）；缺省则不指定印章，
         * 自动签会因「未指定印章」失败。仅在 {@link #companySeal()} 为 true 时需要。
         */
        String companySealId
) {
    public EssFileProperties {
        if (enabled == null) enabled = Boolean.FALSE;
        if (endpoint == null || endpoint.isBlank()) endpoint = "file.test.ess.tencent.cn";
        if (signatureKeyword == null || signatureKeyword.isBlank()) signatureKeyword = "电子签字";
        if (dateKeyword == null) dateKeyword = "";
        if (companySeal == null) companySeal = Boolean.FALSE;
        if (sealKeyword == null || sealKeyword.isBlank()) sealKeyword = "公章";
        if (companyName == null || companyName.isBlank()) companyName = "天津源创智能科技有限公司";
        if (relativeLocation == null || relativeLocation.isBlank()) relativeLocation = "Right";
        if (signWidth == null) signWidth = 120.0;
        if (signHeight == null) signHeight = 44.0;
        if (offsetX == null) offsetX = 5.0;
        if (offsetY == null) offsetY = 0.0;
        if (sealRelativeLocation == null || sealRelativeLocation.isBlank()) sealRelativeLocation = "Below";
        if (sealWidth == null) sealWidth = 100.0;
        if (sealHeight == null) sealHeight = 100.0;
        if (sealOffsetX == null) sealOffsetX = 0.0;
        if (sealOffsetY == null) sealOffsetY = 5.0;
    }
}

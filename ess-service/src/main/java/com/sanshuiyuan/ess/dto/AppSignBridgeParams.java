package com.sanshuiyuan.ess.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * App 签署桥接 DTO。
 * <p>
 * 供 Flutter contract_sign_page.dart 使用的签署参数。
 * 包含 WebView 加载或 URL scheme 跳转所需的参数。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppSignBridgeParams(
        /** 合同 ID */
        Long contractId,
        /** 合同编号 */
        String contractNo,
        /** 客户端类型（固定 APP） */
        String clientType,
        /** H5 签署 URL（WebView 加载地址） */
        String signUrl,
        /** App 签署参数（原生插件桥接参数） */
        Object signParams,
        /** Deep link 回调 scheme */
        String callbackScheme,
        /** 签署完成回调 URL */
        String callbackUrl,
        /** 签署方式 */
        String signMethod
) {
    /**
     * 创建 App 签署桥接参数（WebView 模式）。
     */
    public static AppSignBridgeParams webView(Long contractId, String contractNo,
                                               String signUrl) {
        return new AppSignBridgeParams(
                contractId,
                contractNo,
                "APP",
                signUrl,
                null,
                "sanshuiyuan://sign-complete",
                "sanshuiyuan://sign-complete?contractId=" + contractId,
                "APP_WEBVIEW_SIGN"
        );
    }

    /**
     * 创建 App 签署桥接参数（原生插件模式）。
     */
    public static AppSignBridgeParams nativePlugin(Long contractId, String contractNo,
                                                    Object signParams) {
        return new AppSignBridgeParams(
                contractId,
                contractNo,
                "APP",
                null,
                signParams,
                "sanshuiyuan://sign-complete",
                "sanshuiyuan://sign-complete?contractId=" + contractId,
                "APP_NATIVE_SIGN"
        );
    }
}

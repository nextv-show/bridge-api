package com.sanshuiyuan.ess.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 小程序签署桥接 DTO。
 * <p>
 * 供前端 Taro ContractSignMini.tsx 组件使用的签署参数。
 * 包含 wx.navigateToMiniProgram 所需的所有参数。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MiniSignBridgeParams(
        /** 合同 ID */
        Long contractId,
        /** 合同编号 */
        String contractNo,
        /** 客户端类型（固定 MINI） */
        String clientType,
        /** 签署参数（小程序签署所需的完整 JSON） */
        Object signParams,
        /** 签署完成回调路径 */
        String callbackPath,
        /** 签署来源小程序 appId */
        String wxAppId,
        /** 跳转签署的签署方式 */
        String signMethod
) {
    /**
     * 创建小程序签署桥接参数。
     */
    public static MiniSignBridgeParams of(Long contractId, String contractNo,
                                           Object signParams, String wxAppId) {
        return new MiniSignBridgeParams(
                contractId,
                contractNo,
                "MINI",
                signParams,
                "/pages/contract/complete?contractId=" + contractId,
                wxAppId,
                "MINI_APP_SIGN"
        );
    }
}

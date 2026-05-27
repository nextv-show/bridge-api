package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanshuiyuan.ess.config.ClientTypeInterceptor.ClientType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 多端签署参数适配服务。
 * <p>
 * 根据 client_type (H5/MINI/APP) 自动选择对应的签署参数生成策略，
 * 统一封装签署参数差异，对上层提供一致的调用接口。
 */
@Service
public class MultiPlatformSignService {

    private static final Logger log = LoggerFactory.getLogger(MultiPlatformSignService.class);

    private final EssSignService essSignService;

    public MultiPlatformSignService(EssSignService essSignService) {
        this.essSignService = essSignService;
    }

    /**
     * 多端签署参数结果。
     */
    public record SignParamsResult(
            ClientType clientType,
            String signUrl,          // H5 签署 URL
            JsonNode signParams,     // 小程序/App 签署参数
            String signMethod        // 签署方式描述
    ) {}

    /**
     * 根据客户端类型生成适配签署参数。
     *
     * @param contractId 合同编号（业务 ID）
     * @param signerId   签署人 ID
     * @param clientType 客户端类型
     * @param options    额外选项（jumpUrl, h5Type, wxAppId, appType 等）
     * @return 适配的签署参数
     */
    public SignParamsResult generateSignParams(String contractId, String signerId,
                                                ClientType clientType,
                                                Map<String, String> options) {
        log.info("生成多端签署参数 [contractId={}, signerId={}, clientType={}]",
                contractId, signerId, clientType);

        return switch (clientType) {
            case H5 -> generateH5Params(contractId, signerId, options);
            case MINI -> generateMiniParams(contractId, signerId, options);
            case APP -> generateAppParams(contractId, signerId, options);
        };
    }

    /**
     * 从 contractId 字符串和 Long 型 contractId 自动识别签署流程。
     * 兼容旧的 ContractController 使用 Long id 的场景。
     */
    public SignParamsResult generateSignParamsByLongId(Long contractId, String contractNo,
                                                        String signerId, ClientType clientType,
                                                        Map<String, String> options) {
        return generateSignParams(String.valueOf(contractId), signerId, clientType, options);
    }

    private SignParamsResult generateH5Params(String contractId, String signerId,
                                               Map<String, String> options) {
        String jumpUrl = options != null ? options.getOrDefault("jumpUrl", "") : "";
        String h5Type = options != null ? options.getOrDefault("h5Type", "jump") : "jump";

        String signUrl = essSignService.generateH5SignUrl(contractId, signerId, jumpUrl, h5Type);

        return new SignParamsResult(ClientType.H5, signUrl, null, "H5_SIGN_URL");
    }

    private SignParamsResult generateMiniParams(String contractId, String signerId,
                                                 Map<String, String> options) {
        String wxAppId = options != null ? options.get("wxAppId") : null;

        JsonNode params = essSignService.generateMiniAppSignParams(contractId, signerId, wxAppId);

        return new SignParamsResult(ClientType.MINI, null, params, "MINI_APP_SIGN");
    }

    private SignParamsResult generateAppParams(String contractId, String signerId,
                                                Map<String, String> options) {
        String appType = options != null ? options.getOrDefault("appType", "android") : "android";

        JsonNode params = essSignService.generateAppSignParams(contractId, signerId, appType);

        return new SignParamsResult(ClientType.APP, null, params, "APP_SIGN");
    }

    /**
     * 构建返回给前端的统一响应体。
     */
    public static Map<String, Object> toResponseMap(SignParamsResult result) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("clientType", result.clientType().name());
        resp.put("signMethod", result.signMethod());

        if (result.signUrl() != null) {
            resp.put("signUrl", result.signUrl());
        }
        if (result.signParams() != null) {
            resp.put("signParams", result.signParams());
        }

        return resp;
    }
}

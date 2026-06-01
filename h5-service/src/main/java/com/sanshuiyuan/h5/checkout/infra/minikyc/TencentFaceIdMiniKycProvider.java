package com.sanshuiyuan.h5.checkout.infra.minikyc;

import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 腾讯云人脸核身（FaceID / 慧眼）小程序通道。
 *
 * <p><b>对接说明（待完成真实调用）：</b>本类已就位 provider 契约与小程序端所需 sdkParams 装配，
 * 但腾讯人脸核身需要以下外部前置（运维/产品确认后填入 {@code tencent.faceid.*} 配置）：
 * <ul>
 *   <li>开通腾讯云慧眼/FaceID 实名核身，配置业务流程 {@code ruleId}；</li>
 *   <li>小程序 appId 在 FaceID 控制台加白，并引入对应人脸核身插件；</li>
 *   <li>服务端用 SecretId/SecretKey 调 {@code DetectAuth}/{@code IdCardVerification} 取 BizToken，
 *       核身后调 {@code GetDetectInfoEnhanced} 读结果。</li>
 * </ul>
 * 真实调用接通前，仅当显式配置了 secret-id 才会装配本 Bean（见 MiniKycConfig），
 * 未配置则回退 {@link StubMiniKycProvider}，不影响 dev/test 全链路联调。
 */
public class TencentFaceIdMiniKycProvider implements MiniKycProvider {

    private static final Logger log = LoggerFactory.getLogger(TencentFaceIdMiniKycProvider.class);

    private final String secretId;
    private final String secretKey;
    private final String ruleId;
    private final String region;
    private final String mpAppId;

    public TencentFaceIdMiniKycProvider(String secretId, String secretKey, String ruleId,
                                        String region, String mpAppId) {
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.ruleId = ruleId;
        this.region = region;
        this.mpAppId = mpAppId;
    }

    @Override
    public MiniKycInitResult init(String openid, String realName, String idNo) {
        // TODO(对接): 调腾讯云 DetectAuth/IdCardVerification 取 BizToken + certifyId（绑定 realName/idNo）。
        //  下方装配的是小程序端人脸 SDK 所需的固定参数；BizToken 接通后并入 sdkParams。
        log.warn("腾讯人脸核身 init 尚未接通真实调用（ruleId={}, region={}）", ruleId, region);
        Map<String, Object> sdkParams = new LinkedHashMap<>();
        sdkParams.put("provider", "tencent-faceid");
        sdkParams.put("ruleId", ruleId);
        sdkParams.put("mpAppId", mpAppId);
        throw new BizException(ErrorCode.KYC_INIT_FAILED, "小程序人脸核身尚未完成对接，请联系运维配置");
    }

    @Override
    public MiniKycResult queryResult(String certifyId) {
        // TODO(对接): 调腾讯云 GetDetectInfoEnhanced，按 certifyId 读核身结论。
        log.warn("腾讯人脸核身 queryResult 尚未接通真实调用 certifyId={}", certifyId);
        throw new BizException(ErrorCode.KYC_QUERY_FAILED, "小程序人脸核身尚未完成对接，请联系运维配置");
    }
}

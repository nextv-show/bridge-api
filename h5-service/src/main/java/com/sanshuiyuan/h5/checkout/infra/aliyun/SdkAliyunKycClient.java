package com.sanshuiyuan.h5.checkout.infra.aliyun;

import com.aliyun.cloudauth20190307.Client;
import com.aliyun.cloudauth20190307.models.DescribeFaceVerifyRequest;
import com.aliyun.cloudauth20190307.models.DescribeFaceVerifyResponseBody;
import com.aliyun.cloudauth20190307.models.InitFaceVerifyRequest;
import com.aliyun.cloudauth20190307.models.InitFaceVerifyResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 阿里云活体检测方案（Cloudauth 2019-03-07，ProductCode=LR_FR）真实实现。
 * 三水元 H5 场景为「活体检测」：用户完成活体动作核验，DescribeFaceVerify 返回 Passed + 人脸/设备资料，
 * 不返回姓名/身份证号（如需实名身份信息，需改用核身方案或额外采集，见 KycVerifyUseCase 注释）。
 * H5 跳转流程：InitFaceVerify 拿 CertifyUrl → 前端跳转 → 回跳（ReturnUrl?response=...）→ DescribeFaceVerify 查权威结果。
 */
public class SdkAliyunKycClient implements AliyunKycClient {

    private static final Logger log = LoggerFactory.getLogger(SdkAliyunKycClient.class);

    private final Client client;
    private final Long sceneId;
    private final String productCode;
    private final String model;

    public SdkAliyunKycClient(String accessKeyId, String accessKeySecret,
                              String endpoint, Long sceneId, String productCode, String model) {
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint(endpoint);
            this.client = new Client(config);
        } catch (Exception e) {
            throw new IllegalStateException("初始化阿里云实人认证客户端失败", e);
        }
        this.sceneId = sceneId;
        this.productCode = productCode;
        this.model = model;
    }

    @Override
    public KycInitResult init(String openid, String metaInfo, String returnUrl) {
        InitFaceVerifyRequest req = new InitFaceVerifyRequest()
                .setSceneId(sceneId)
                .setOuterOrderNo("KYC" + System.currentTimeMillis()
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .setProductCode(productCode)   // LR_FR
                .setModel(model)               // 活体检测类型，如 MOVE_ACTION
                .setUserId(openid)             // 必填：业务用户唯一标识
                .setCertifyUrlType("H5")
                .setMetaInfo(metaInfo)
                .setReturnUrl(returnUrl);
        try {
            InitFaceVerifyResponseBody body = client.initFaceVerify(req).getBody();
            if (body == null || body.getResultObject() == null) {
                throw new BizException(ErrorCode.KYC_INIT_FAILED, "实名认证初始化无返回");
            }
            String certifyId = body.getResultObject().getCertifyId();
            String certifyUrl = body.getResultObject().getCertifyUrl();
            if (certifyId == null || certifyUrl == null) {
                log.warn("InitFaceVerify 返回缺字段 code={}", body.getCode());
                throw new BizException(ErrorCode.KYC_INIT_FAILED, "实名认证初始化失败");
            }
            return new KycInitResult(certifyId, null, certifyUrl);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用阿里云 InitFaceVerify 失败", e);
            throw new BizException(ErrorCode.KYC_INIT_FAILED, "实名认证服务暂不可用");
        }
    }

    @Override
    public KycVerifyResult queryResult(String certifyId) {
        DescribeFaceVerifyRequest req = new DescribeFaceVerifyRequest()
                .setSceneId(sceneId)
                .setCertifyId(certifyId);
        try {
            DescribeFaceVerifyResponseBody body = client.describeFaceVerify(req).getBody();
            if (body == null || body.getResultObject() == null) {
                return new KycVerifyResult(false, null, null);
            }
            boolean passed = "T".equalsIgnoreCase(body.getResultObject().getPassed());
            // LR_FR 活体检测方案不返回姓名/身份证号，仅返回 Passed + 人脸资料；身份信息留空。
            return new KycVerifyResult(passed, null, null);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用阿里云 DescribeFaceVerify 失败 certifyId={}", certifyId, e);
            throw new BizException(ErrorCode.KYC_QUERY_FAILED, "实名结果查询暂不可用");
        }
    }
}

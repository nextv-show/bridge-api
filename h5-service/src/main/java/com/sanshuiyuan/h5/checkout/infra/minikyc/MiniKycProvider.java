package com.sanshuiyuan.h5.checkout.infra.minikyc;

import java.util.Map;

/**
 * 小程序原生实人认证（人脸核身）provider 抽象。可插拔：腾讯人脸核身 / 微信实名 / 桩。
 *
 * <p>与 H5 的阿里云网页活体（{@link com.sanshuiyuan.h5.checkout.infra.aliyun.AliyunKycClient}）相比，
 * 仅“活体提供方”不同——落库、加密、一证一号、supersede 等全部复用既有 KYC 逻辑。
 */
public interface MiniKycProvider {

    /**
     * 发起核身：绑定姓名+身份证号，返回 certifyId 与小程序端 SDK 直接消费的参数。
     *
     * @param openid   统一身份（仅用于拼业务流水号/追踪）。
     * @param realName 真实姓名。
     * @param idNo     身份证号（已规整大写）。
     */
    MiniKycInitResult init(String openid, String realName, String idNo);

    /** 查询核身结果（权威，不信任前端）。 */
    MiniKycResult queryResult(String certifyId);

    /**
     * @param certifyId 本次核身流水号（落 INIT 记录的 certify_id）。
     * @param sdkParams 小程序端调用人脸 SDK 所需参数（含 provider 标识，前端按 provider 分支）。
     */
    record MiniKycInitResult(String certifyId, Map<String, Object> sdkParams) {}

    /** @param passed 是否核身通过。 */
    record MiniKycResult(boolean passed) {}
}

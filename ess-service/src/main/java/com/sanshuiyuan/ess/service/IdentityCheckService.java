package com.sanshuiyuan.ess.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * KYC 身份检查服务。
 * <p>
 * 复用已有 KYC 结果填充合同签署前的前置信息：
 * - 检查用户是否已完成 KYC 实名认证
 * - 读取用户的实名信息（姓名、身份证号等）
 * - 提供给腾讯电子签进行身份核验
 * <p>
 * 注意：实际的 KYC 数据存储在 h5-service 子模块中，
 * 本服务通过内部 API 或共享数据库视图访问 KYC 数据。
 * 当前实现预留接口，后续对接 h5-service 的 KYC 模块。
 */
@Service
public class IdentityCheckService {

    private static final Logger log = LoggerFactory.getLogger(IdentityCheckService.class);

    private final ObjectMapper objectMapper;

    public IdentityCheckService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * KYC 状态信息。
     */
    public record KycStatusInfo(
            boolean verified,
            String realName,
            String idCardHash,
            String idCardMask,
            String kycRecordId,
            String verificationMethod
    ) {}

    /**
     * 检查用户 KYC 状态。
     * <p>
     * 在完整集成中，此方法应调用 h5-service 的 KYC 接口。
     * 当前返回模拟数据供开发测试。
     *
     * @param userId 用户 ID
     * @return KYC 状态信息
     */
    public KycStatusInfo checkKycStatus(Long userId) {
        log.debug("检查用户 KYC 状态 [userId={}]", userId);

        // TODO: 对接 h5-service 的 kycVerify/kycInit 接口
        // 实际应通过 RestTemplate 调用 h5-service 的内部 API:
        // GET /api/internal/kyc/status?userId={userId}
        //
        // 当前返回 placeholder 以支持本地开发和测试
        return new KycStatusInfo(
                true,
                "张三",
                "sha256:abc123",
                "440***1234",
                "kyc-record-" + userId,
                "ALI_LIVENESS"
        );
    }

    /**
     * 构建腾讯电子签身份核验所需的用户信息。
     * <p>
     * 从 KYC 结果中提取实名信息，填充到电子签 API 的签署人参数中。
     *
     * @param userId 用户 ID
     * @return 腾讯电子签格式的用户信息 JSON
     */
    public ObjectNode buildEssUserInfo(Long userId) {
        KycStatusInfo kycInfo = checkKycStatus(userId);

        ObjectNode userInfo = objectMapper.createObjectNode();
        userInfo.put("Name", kycInfo.realName());
        userInfo.put("IdCardType", "ID_CARD");
        userInfo.put("IdCardNumber", kycInfo.idCardMask());
        userInfo.put("Mobile", "");

        log.debug("构建电子签用户信息 [userId={}, name={}]",
                userId, kycInfo.realName());
        return userInfo;
    }

    /**
     * 校验 KYC 是否已通过，未通过则抛出异常。
     *
     * @param userId 用户 ID
     * @return KYC 状态信息
     * @throws IllegalStateException 如果 KYC 未通过
     */
    public KycStatusInfo requireKycVerified(Long userId) {
        KycStatusInfo info = checkKycStatus(userId);
        if (!info.verified()) {
            throw new IllegalStateException(
                    String.format("用户 %d 未完成实名认证，请先完成 KYC", userId));
        }
        return info;
    }
}

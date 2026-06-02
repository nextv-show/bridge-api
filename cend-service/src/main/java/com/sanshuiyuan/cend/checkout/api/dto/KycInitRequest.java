package com.sanshuiyuan.cend.checkout.api.dto;

/**
 * KYC 初始化入参。
 * - metaInfo：前端阿里云 getMetaInfo() 采集的设备指纹。
 * - realName / idCardNo / phone：前端采集的实名信息（LR_FR 活体方案不回传身份信息）。
 *   phone 用于腾讯电子签合同签署。
 * 已实名用户这些字段可为空——后端不会再发起认证。
 */
public record KycInitRequest(String metaInfo, String realName, String idCardNo, String phone) {}

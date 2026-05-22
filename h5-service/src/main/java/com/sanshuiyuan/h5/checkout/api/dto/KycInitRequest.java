package com.sanshuiyuan.h5.checkout.api.dto;

/**
 * KYC 初始化入参。
 * - metaInfo：前端阿里云 getMetaInfo() 采集的设备指纹，不可硬编码。
 * - realName / idCardNo：前端额外采集的实名信息（LR_FR 活体方案不回传身份信息，由前端采集、后端加密绑定）。
 * 已实名用户（命中 PASS 记录）这些字段可为空——后端不会再发起认证。
 */
public record KycInitRequest(String metaInfo, String realName, String idCardNo) {}

package com.sanshuiyuan.h5.checkout.api.dto;

/**
 * 小程序 KYC 初始化入参。realName / idCardNo / phone 为前端采集的实名信息；
 * phone 用于后续腾讯电子签合同签署。已实名用户这些字段可为空。
 */
public record MiniKycInitRequest(String realName, String idCardNo, String phone) {}

package com.sanshuiyuan.user.api.dto;

/** 更新资料：小程序「头像昵称填写」回传。avatarUrl 可空（设计用首字母头像）。 */
public record UpdateProfileRequest(String nickname, String avatarUrl) {}

package com.sanshuiyuan.user.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * H5 登录成功后回传的账号同步入参（spec 012）。
 *
 * @param openid    H5 公众号网页授权 openid（必填，落入 users.openid_wx）。
 * @param unionid   微信开放平台 unionid（可选，多端账号统一键）。无则按 openid 查/建。
 * @param inviterId 推广者 user_id（可选，已由 H5 端 RefIdCodec 解密为明文 Long）。
 *                  仅首次创建时写入 L1 关系链；严禁接受明文外部注入以外的来源（合规铁律）。
 */
public record SyncH5Request(
        @NotBlank String openid,
        String unionid,
        Long inviterId) {
}

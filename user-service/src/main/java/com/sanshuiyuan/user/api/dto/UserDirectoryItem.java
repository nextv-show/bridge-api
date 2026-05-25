package com.sanshuiyuan.user.api.dto;

/**
 * /internal/users 导出条目 — 供 admin-service 同步真实用户目录。
 * createdAt 为 ISO-8601 字符串。
 */
public record UserDirectoryItem(
        Long id,
        String unionid,
        String openidWx,
        String openidApp,
        String nickname,
        String avatarUrl,
        String activeRole,
        String createdAt) {
}

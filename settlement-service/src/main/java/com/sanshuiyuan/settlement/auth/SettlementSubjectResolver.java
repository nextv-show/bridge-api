package com.sanshuiyuan.settlement.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 把 H5 JWT subject 解析为 users.id。
 * H5/小程序的统一身份 token subject 是 openid/unionid（非数字）；H5 网页端可能直接是数字 user_id。
 * 这里数字直接透传，否则按 openid 查 core_db.users.id（只读，不创建）。
 */
@Component
public class SettlementSubjectResolver {

    private final JdbcTemplate jdbc;

    public SettlementSubjectResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return users.id；无法解析返回 null（视为未授权）。 */
    public Long resolveUserId(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException ignore) {
            // openid / unionid
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM users WHERE openid = ?", Long.class, subject);
        return ids.isEmpty() ? null : ids.get(0);
    }
}

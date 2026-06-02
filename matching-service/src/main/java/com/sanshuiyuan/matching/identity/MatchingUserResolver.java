package com.sanshuiyuan.matching.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 把 JWT subject（openid/unionid）解析为 h5_db.users.id（= device_assets.user_id）。
 * 复刻 h5-service AdminOrderProjector.resolveUserId：select-then-insert，应用层去重。
 * Owner 门控：device_assets 中存在 stage=PENDING_MATCH 的资产。device_assets 为 admin 真表，只读。
 */
@Component
public class MatchingUserResolver {

    private static final Logger log = LoggerFactory.getLogger(MatchingUserResolver.class);

    private final JdbcTemplate jdbc;

    public MatchingUserResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 只读解析 openid→users.id，**不创建**（供 GET/只读事务用，避免在 readOnly 事务内写库 + 浏览即污染 users 表）。
     * 未建过 users 行的用户必然没有需求单、也不可能是 owner，故返回空即可。
     */
    public Optional<Long> findUserId(String openid) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM users WHERE openid = ?", Long.class, openid);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    /** 解析 openid→users.id；缺则懒创建 users 行（昵称/头像取自 h5_users，缺省 'H5用户'）。仅写路径（发布需求）调用。 */
    public long resolveUserId(String openid) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM users WHERE openid = ?", Long.class, openid);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return insertUser(openid);
    }

    private long insertUser(String openid) {
        String nickname = "H5用户";
        String avatarUrl = null;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT nickname, avatar_url FROM h5_users WHERE openid = ?", openid);
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            Object n = row.get("nickname");
            if (n != null && !n.toString().isBlank()) {
                nickname = n.toString();
            }
            Object a = row.get("avatar_url");
            avatarUrl = a != null ? a.toString() : null;
        }

        final String finalNickname = nickname;
        final String finalAvatarUrl = avatarUrl;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (openid, nickname, avatar_url, channel, tier, tags, status, kyc_status, " +
                            "created_at, updated_at) VALUES (?, ?, ?, 'WECHAT_MP', 'NORMAL', '', 'ACTIVE', 'NONE', NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, openid);
            ps.setString(2, finalNickname);
            ps.setString(3, finalAvatarUrl);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入 users 未返回自增主键 openid=" + openid);
        }
        log.info("撮合：懒创建 users 行 openid={} id={}", openid, key.longValue());
        return key.longValue();
    }

    /** Owner 门控：是否持有至少一台 PENDING_MATCH 资产（可接单）。 */
    public boolean isOwner(long userId) {
        Long cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM device_assets WHERE user_id = ? AND stage = 'PENDING_MATCH'",
                Long.class, userId);
        return cnt != null && cnt > 0;
    }
}

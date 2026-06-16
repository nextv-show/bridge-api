package com.sanshuiyuan.settlement.application.payout;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** 解析 owner 的小程序 openid（商家转账收款人）。settlement 连 core_db，直读 users.openid。 */
@Component
public class OwnerOpenidResolver {

    private final JdbcTemplate jdbc;

    public OwnerOpenidResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> findOpenid(long userId) {
        List<String> rows = jdbc.queryForList("SELECT openid FROM users WHERE id = ?", String.class, userId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        String openid = rows.get(0);
        return (openid == null || openid.isBlank()) ? Optional.empty() : Optional.of(openid);
    }
}

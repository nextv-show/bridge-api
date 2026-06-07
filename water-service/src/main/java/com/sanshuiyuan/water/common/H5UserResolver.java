package com.sanshuiyuan.water.common;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * openid → user_id 解析器。water-service 连接的是同一 MySQL 实例（water_db），
 * 通过跨库查询 {@code core_db.h5_users} 拿到统一身份的数值主键。
 * H5JwtFilter 注入的 principal 即 openid。
 */
@Service
public class H5UserResolver {

    private final JdbcTemplate jdbcTemplate;

    public H5UserResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 解析 openid 到 core_db.h5_users.id；找不到抛 {@link ErrorCode#UNAUTHORIZED}。 */
    public Long resolveUserId(String openid) {
        if (openid == null || openid.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少登录身份");
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM core_db.h5_users WHERE openid = ?", Long.class, openid);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
    }
}

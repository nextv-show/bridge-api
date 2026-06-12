package com.sanshuiyuan.matching.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * P1-3 发需求实名门控（design §11，覆盖 spec FR-1.4）。
 * core_db.kyc_records（cend-service 写、matching-service 同库只读）持 openid↔实名状态映射，
 * 按当前 openid 是否存在 PASS 记录判定。仅判 PASS（INIT/FAIL/SUPERSEDED 不算实名）。
 * 注：跨 openid「同证同人」聚合属 cend IdentityResolver 内部能力，本门控只认本端 openid 的 PASS。
 */
@Component
public class KycGuard {

    private final JdbcTemplate jdbc;

    public KycGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 当前 openid 是否已通过实名（存在 status=PASS 的 kyc 记录）。 */
    public boolean hasPassedKyc(String openid) {
        Long cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kyc_records WHERE openid = ? AND status = 'PASS'",
                Long.class, openid);
        return cnt != null && cnt > 0;
    }
}

package com.sanshuiyuan.matching.identity;

import com.sanshuiyuan.matching.crypto.IdCardCipher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * P1-3 发需求实名门控（design §11，覆盖 spec FR-1.4）。
 * core_db.kyc_records（cend-service 写、matching-service 同库只读）持 openid↔实名状态映射，
 * 按当前 openid 是否存在 PASS 记录判定。仅判 PASS（INIT/FAIL/SUPERSEDED 不算实名）。
 * 注：跨 openid「同证同人」聚合属 cend IdentityResolver 内部能力，本门控只认本端 openid 的 PASS。
 */
@Component
public class KycGuard {

    private final JdbcTemplate jdbc;
    private final IdCardCipher cipher;

    public KycGuard(JdbcTemplate jdbc, IdCardCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    /** 当前 openid 是否已通过实名（存在 status=PASS 的 kyc 记录）。 */
    public boolean hasPassedKyc(String openid) {
        Long cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kyc_records WHERE openid = ? AND status = 'PASS'",
                Long.class, openid);
        return cnt != null && cnt > 0;
    }

    /**
     * 从最近一条 PASS 的 kyc 记录解密取实名姓名和手机号。
     * matching-service 与 cend-service 共享同一 AES master key (H5_AES_MASTER_KEY)，
     * 因此可以用本地 IdCardCipher 解密 cend-service 加密写入的 kyc_records.real_name / phone_enc。
     *
     * @return Optional.empty() 如果该 openid 没有 PASS 记录；
     *         内部 DTO 持有解密后的 realName (String) 和 phone (String)，均可能为 null（如果原始记录无该字段）
     */
    public Optional<KycContactInfo> findContactInfo(String openid) {
        return jdbc.query(
                "SELECT real_name, phone_enc FROM kyc_records "
                        + "WHERE openid = ? AND status = 'PASS' ORDER BY verified_at DESC LIMIT 1",
                rs -> {
                    if (!rs.next()) {
                        return Optional.<KycContactInfo>empty();
                    }
                    String realName = decryptSafe(rs.getBytes("real_name"));
                    String phone = decryptSafe(rs.getBytes("phone_enc"));
                    return Optional.of(new KycContactInfo(realName, phone));
                },
                openid);
    }

    /**
     * 安全解密：密钥不一致或数据损坏时返回 null 而非抛异常。
     * findContactInfo 仅用于联系人信息自动补全（便利功能），解密失败不应阻断发需求主流程。
     * 此场景下用户需手动填写联系人姓名/手机号。
     */
    private String decryptSafe(byte[] encrypted) {
        if (encrypted == null) return null;
        try {
            return cipher.decrypt(encrypted);
        } catch (Exception e) {
            return null;
        }
    }

    /** PASS 实名记录解密后的联系人信息（realName / phone 均可能为 null）。 */
    public record KycContactInfo(String realName, String phone) {}
}

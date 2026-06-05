package com.sanshuiyuan.admin.infra.repository;

import com.sanshuiyuan.admin.domain.KycRecord;
import com.sanshuiyuan.admin.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 灵活分页查询：status / channel / tier / tagLike 均可空（空则不过滤），
     * keyword 匹配 id（精确转字符串模糊）/phone_mask/openid/nickname/real_name_mask。
     * tab 概念在 Service 层转换为这些过滤条件。
     *
     * <p>实名筛选改为按权威 {@code kyc_records}（openid 关联）实时派生，不再读已废弃的去规范化
     * {@code users.kyc_status}：{@code kycFilter='PASS'} → 有 PASS 记录；{@code 'PENDING'} →
     * 有 PENDING 记录且无 PASS（即待审而未通过）。passStatus/pendStatus 以枚举参数下传，避免
     * 在 JPQL 写枚举字面量。
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:status IS NULL OR u.status = :status)
              AND (:statusIn IS NULL OR u.status IN :statusIn)
              AND (:channel IS NULL OR u.channel = :channel)
              AND (:tier IS NULL OR u.tier = :tier)
              AND (:kycFilter IS NULL
                   OR (:kycFilter = 'PASS'
                       AND EXISTS (SELECT 1 FROM KycRecord k WHERE k.openid = u.openid AND k.status = :passStatus))
                   OR (:kycFilter = 'PENDING'
                       AND EXISTS (SELECT 1 FROM KycRecord kp WHERE kp.openid = u.openid AND kp.status = :pendStatus)
                       AND NOT EXISTS (SELECT 1 FROM KycRecord kx WHERE kx.openid = u.openid AND kx.status = :passStatus)))
              AND (:tagLike IS NULL OR u.tags LIKE :tagLike)
              AND (:keyword IS NULL OR
                   CAST(u.id AS string) LIKE :keyword OR
                   u.phoneMask LIKE :keyword OR
                   u.openid LIKE :keyword OR
                   u.nickname LIKE :keyword OR
                   u.realNameMask LIKE :keyword)
            """)
    Page<User> search(@Param("status") String status,
                       @Param("statusIn") Collection<String> statusIn,
                       @Param("channel") String channel,
                       @Param("tier") String tier,
                       @Param("kycFilter") String kycFilter,
                       @Param("passStatus") KycRecord.Status passStatus,
                       @Param("pendStatus") KycRecord.Status pendStatus,
                       @Param("tagLike") String tagLike,
                       @Param("keyword") String keyword,
                       Pageable pageable);

    long countByStatusIn(Collection<String> statuses);

    /** 实名通过数：有任一 PASS 记录的用户数（按 kyc_records 实时派生）。 */
    @Query("""
            SELECT COUNT(u) FROM User u
            WHERE EXISTS (SELECT 1 FROM KycRecord k WHERE k.openid = u.openid AND k.status = :passStatus)
            """)
    long countKycPass(@Param("passStatus") KycRecord.Status passStatus);

    /** 待审数：有 PENDING 记录且无 PASS 记录的用户数。 */
    @Query("""
            SELECT COUNT(u) FROM User u
            WHERE EXISTS (SELECT 1 FROM KycRecord kp WHERE kp.openid = u.openid AND kp.status = :pendStatus)
              AND NOT EXISTS (SELECT 1 FROM KycRecord kx WHERE kx.openid = u.openid AND kx.status = :passStatus)
            """)
    long countKycPending(@Param("pendStatus") KycRecord.Status pendStatus,
                         @Param("passStatus") KycRecord.Status passStatus);

    /** 今日新增：createdAt >= 当日 0 点。 */
    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    /** N 日活跃：lastActiveAt >= 阈值。注意 lastActiveAt 当前由 sync 写入，非真实活跃埋点。 */
    long countByLastActiveAtGreaterThanEqual(LocalDateTime since);

    @Query("SELECT COUNT(u) FROM User u WHERE u.tags LIKE %:code%")
    long countByTagLike(@Param("code") String code);

    List<User> findByStatusIn(Collection<String> statuses);
}

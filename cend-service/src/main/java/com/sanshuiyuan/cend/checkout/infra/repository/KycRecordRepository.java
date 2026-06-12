package com.sanshuiyuan.cend.checkout.infra.repository;

import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycRecordRepository extends JpaRepository<KycRecord, Long> {

    Optional<KycRecord> findFirstByOpenidAndStatusOrderByVerifiedAtDesc(String openid, KycStatus status);

    List<KycRecord> findAllByOpenidAndStatus(String openid, KycStatus status);

    /**
     * 批量取一组 openid 下指定状态（PASS）的实名记录，用于「我的推荐」列表展示名兜底（实名脱敏/手机尾号），避免 N+1。
     * 仅命中 idx_kyc_openid，仅取脱敏字段（real_name_mask / phone_mask）使用，绝不解密明文。
     */
    List<KycRecord> findAllByOpenidInAndStatus(java.util.Collection<String> openids, KycStatus status);

    Optional<KycRecord> findFirstByCertifyIdAndOpenidAndStatus(String certifyId, String openid, KycStatus status);

    /** 一证一号：同一身份证哈希在「非该 openid」下是否已存在指定状态（PASS）记录。 */
    boolean existsByIdCardHashAndStatusAndOpenidNot(String idCardHash, KycStatus status, String openid);

    /**
     * 跨端身份聚合：取同一身份证哈希下指定状态（PASS）的全部实名记录。
     *
     * <p>用于把"同证同人"在多端（公众号/小程序）各自的 openid 归并为一个自然人，供"我的订单"按自然人聚合可见。
     * 仅基于已活体核验的 PASS 记录，绝不使用 INIT（未核验，按其 hash 聚合会造成 IDOR 越权）。
     * 按 {@code id_card_hash} 单点查询，命中既有索引 idx_kyc_id_card_hash，不涉及关系链层级。
     */
    List<KycRecord> findAllByIdCardHashAndStatus(String idCardHash, KycStatus status);

    /** 微信手机号核验跨端关联：按手机号哈希取同人最近一条指定状态（PASS）实名记录，用于解析其 id_card_hash。 */
    Optional<KycRecord> findFirstByPhoneHashAndStatusOrderByVerifiedAtDesc(String phoneHash, KycStatus status);

    /** phone_hash 回填：取一批已实名(PASS)、有密文手机号但缺 phone_hash 的记录。 */
    List<KycRecord> findByStatusAndPhoneHashIsNullAndPhoneEncIsNotNull(
            KycStatus status, org.springframework.data.domain.Pageable pageable);

    /** id_card_hash 回填：取一批已实名(PASS)、有密文身份证但缺 id_card_hash 的记录（V022 之前的存量）。 */
    List<KycRecord> findByStatusAndIdCardHashIsNullAndIdCardNoEncIsNotNull(
            KycStatus status, org.springframework.data.domain.Pageable pageable);
}

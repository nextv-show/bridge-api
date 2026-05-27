package com.sanshuiyuan.ess.infra.repository;

import com.sanshuiyuan.ess.domain.ContractIdentityVerification;
import com.sanshuiyuan.ess.domain.ContractIdentityVerification.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 合同身份核验记录仓储。
 */
public interface ContractIdentityVerificationRepository extends JpaRepository<ContractIdentityVerification, Long> {

    /**
     * 查找指定合同的最新核验记录。
     */
    Optional<ContractIdentityVerification> findTopByContractIdOrderByCreatedAtDesc(String contractId);

    /**
     * 查找指定合同的所有核验记录。
     */
    List<ContractIdentityVerification> findByContractIdOrderByCreatedAtDesc(String contractId);

    /**
     * 查找指定用户和合同的最新核验记录。
     */
    Optional<ContractIdentityVerification> findTopByContractIdAndUserIdOrderByCreatedAtDesc(
            String contractId, Long userId);

    /**
     * 统计指定用户当日的核验次数（用于重试限制）。
     */
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

    /**
     * 查找指定用户当日失败的核验记录。
     */
    List<ContractIdentityVerification> findByUserIdAndStatusAndCreatedAtAfter(
            Long userId, Status status, LocalDateTime after);

    /**
     * 查找指定合同的通过记录。
     */
    Optional<ContractIdentityVerification> findByContractIdAndStatus(String contractId, Status status);

    /**
     * 根据腾讯电子签核验 ID 查找记录。
     */
    Optional<ContractIdentityVerification> findByEssVerificationId(String essVerificationId);
}

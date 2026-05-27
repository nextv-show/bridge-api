package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.Contract;
import com.sanshuiyuan.ess.domain.ContractCooldownRecord;
import com.sanshuiyuan.ess.domain.ContractCooldownRecord.CooldownStatus;
import com.sanshuiyuan.ess.infra.repository.ContractCooldownRecordRepository;
import com.sanshuiyuan.ess.infra.repository.ContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 冷静期服务。
 * <p>
 * 管理冷静期的创建、状态查询、过期检测。
 * 24 小时冷静期从支付成功开始计算。
 */
@Service
public class CooldownService {

    private static final Logger log = LoggerFactory.getLogger(CooldownService.class);

    /** 默认冷静期时长（小时） */
    public static final int DEFAULT_COOLDOWN_HOURS = 24;

    private final ContractCooldownRecordRepository cooldownRecordRepository;
    private final ContractRepository contractRepository;
    private final AuditTrailService auditTrailService;

    public CooldownService(ContractCooldownRecordRepository cooldownRecordRepository,
                            ContractRepository contractRepository,
                            AuditTrailService auditTrailService) {
        this.cooldownRecordRepository = cooldownRecordRepository;
        this.contractRepository = contractRepository;
        this.auditTrailService = auditTrailService;
    }

    /**
     * 创建冷静期记录。
     * <p>
     * 幂等：若已存在则返回已有记录。
     *
     * @param contractId 合同 ID
     * @param orderId    订单 ID
     * @param userId     用户 ID
     * @return 冷静期记录
     */
    @Transactional
    public ContractCooldownRecord createCooldown(Long contractId, String orderId, Long userId) {
        // 幂等检查
        var existing = cooldownRecordRepository.findByContractId(contractId);
        if (existing.isPresent()) {
            log.info("冷静期记录已存在，跳过创建 [contractId={}, status={}]",
                    contractId, existing.get().getStatus());
            return existing.get();
        }

        LocalDateTime now = LocalDateTime.now();
        ContractCooldownRecord record = ContractCooldownRecord.create(
                contractId, orderId, userId, now, DEFAULT_COOLDOWN_HOURS);

        record = cooldownRecordRepository.save(record);

        log.info("冷静期已创建 [contractId={}, orderId={}, 结束时间={}]",
                contractId, orderId, record.getCooldownEndAt());

        // 审计事件
        auditTrailService.recordSystemEvent(contractId,
                com.sanshuiyuan.ess.domain.ContractAuditTrail.Action.REVOKE,
                String.format("{\"action\":\"COOLDOWN_CREATED\",\"cooldownEndAt\":\"%s\"}",
                        record.getCooldownEndAt()));

        return record;
    }

    /**
     * 查询冷静期状态。
     *
     * @param contractId 合同 ID
     * @return 冷静期记录（含剩余时间等计算结果）
     */
    @Transactional(readOnly = true)
    public CooldownStatusResult getCooldownStatus(Long contractId) {
        ContractCooldownRecord record = cooldownRecordRepository.findByContractId(contractId)
                .orElseThrow(() -> new IllegalArgumentException("冷静期记录不存在: contractId=" + contractId));

        boolean isActive = record.getStatus() == CooldownStatus.ACTIVE && !record.isExpired();
        long remainingSeconds = isActive ? record.getRemainingSeconds() : 0;

        // 自动修正：如果 DB 状态仍为 ACTIVE 但实际已过期
        if (record.getStatus() == CooldownStatus.ACTIVE && record.isExpired()) {
            remainingSeconds = 0;
            isActive = false;
        }

        return new CooldownStatusResult(
                record.getId(),
                record.getContractId(),
                record.getOrderId(),
                record.getStatus().name(),
                isActive,
                record.getCooldownStartAt(),
                record.getCooldownEndAt(),
                remainingSeconds,
                record.getRevokedAt(),
                record.getRevokeReason()
        );
    }

    /**
     * 检查合同是否在冷静期内。
     *
     * @param contractId 合同 ID
     * @return true=冷静期内，false=冷静期外或不存在
     */
    @Transactional(readOnly = true)
    public boolean isInCooldown(Long contractId) {
        return cooldownRecordRepository.findByContractId(contractId)
                .map(record -> record.getStatus() == CooldownStatus.ACTIVE && !record.isExpired())
                .orElse(false);
    }

    /**
     * 获取已过期的冷静期记录（供定时任务使用）。
     *
     * @return 需要标记过期的记录列表
     */
    @Transactional(readOnly = true)
    public List<ContractCooldownRecord> findExpiredCooldowns() {
        LocalDateTime now = LocalDateTime.now();
        return cooldownRecordRepository.findByStatusAndCooldownEndAtBefore(CooldownStatus.ACTIVE, now);
    }

    /**
     * 标记过期记录。
     *
     * @return 标记的记录数量
     */
    @Transactional
    public int markExpiredCooldowns() {
        List<ContractCooldownRecord> expired = findExpiredCooldowns();
        int count = 0;
        for (ContractCooldownRecord record : expired) {
            record.markExpired();
            cooldownRecordRepository.save(record);
            count++;
            log.info("冷静期已过期 [contractId={}, cooldownEndAt={}]",
                    record.getContractId(), record.getCooldownEndAt());
        }
        return count;
    }

    /**
     * 冷静期状态查询结果。
     */
    public record CooldownStatusResult(
            Long id,
            Long contractId,
            String orderId,
            String status,
            boolean inCooldown,
            LocalDateTime cooldownStartAt,
            LocalDateTime cooldownEndAt,
            long remainingSeconds,
            LocalDateTime revokedAt,
            String revokeReason
    ) {}
}

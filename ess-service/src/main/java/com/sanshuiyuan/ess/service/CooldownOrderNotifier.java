package com.sanshuiyuan.ess.service;

import com.sanshuiyuan.ess.domain.ContractCooldownRecord;
import com.sanshuiyuan.ess.domain.ContractCooldownRecord.CooldownStatus;
import com.sanshuiyuan.ess.infra.repository.ContractCooldownRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 冷静期 → 订单状态联动通知。
 * <p>
 * 冷静期状态变更时通知订单系统进行相应处理。
 */
@Service
public class CooldownOrderNotifier {

    private static final Logger log = LoggerFactory.getLogger(CooldownOrderNotifier.class);

    private final ContractCooldownRecordRepository cooldownRecordRepository;

    public CooldownOrderNotifier(ContractCooldownRecordRepository cooldownRecordRepository) {
        this.cooldownRecordRepository = cooldownRecordRepository;
    }

    /**
     * 批量通知冷静期已过期的订单。
     * <p>
     * 查找所有刚刚过期的记录，发出订单状态变更通知。
     */
    @Transactional
    public void notifyCooldownExpiredBatch() {
        List<ContractCooldownRecord> expiredRecords =
                cooldownRecordRepository.findByStatus(CooldownStatus.EXPIRED);

        for (ContractCooldownRecord record : expiredRecords) {
            try {
                notifyCooldownExpired(record);
            } catch (Exception e) {
                log.warn("通知冷静期过期失败 [contractId={}]: {}",
                        record.getContractId(), e.getMessage());
            }
        }
    }

    /**
     * 通知单个合同的冷静期已过期。
     * <p>
     * 订单系统收到后可更新订单状态为"冷静期已过"。
     */
    public void notifyCooldownExpired(ContractCooldownRecord record) {
        log.info("通知订单冷静期已过期 [contractId={}, orderId={}, userId={}]",
                record.getContractId(), record.getOrderId(), record.getUserId());

        // TODO: 实际调用订单服务 API 通知
        // 当前为内部事件通知，实际项目中可通过消息队列或 HTTP 调用订单服务
    }

    /**
     * 通知冷静期内撤销。
     */
    public void notifyCooldownRevoked(ContractCooldownRecord record) {
        log.info("通知订单冷静期内撤销 [contractId={}, orderId={}, userId={}]",
                record.getContractId(), record.getOrderId(), record.getUserId());

        // TODO: 实际调用订单服务 API 通知退款/取消
    }
}

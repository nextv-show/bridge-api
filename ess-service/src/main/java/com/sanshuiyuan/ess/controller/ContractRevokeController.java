package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.domain.ContractCooldownRecord;
import com.sanshuiyuan.ess.domain.ContractCooldownRecord.CooldownStatus;
import com.sanshuiyuan.ess.infra.repository.ContractCooldownRecordRepository;
import com.sanshuiyuan.ess.service.ContractRevokeService;
import com.sanshuiyuan.ess.service.ContractRevokeService.RevokeResult;
import com.sanshuiyuan.ess.service.IdempotentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 合同撤销控制器。
 * <p>
 * 冷静期内撤销合同端点，含全局幂等处理。
 */
@RestController
@RequestMapping("/api/h5/contracts")
public class ContractRevokeController {

    private static final Logger log = LoggerFactory.getLogger(ContractRevokeController.class);

    private final ContractRevokeService revokeService;
    private final ContractCooldownRecordRepository cooldownRecordRepository;
    private final IdempotentService idempotentService;

    public ContractRevokeController(ContractRevokeService revokeService,
                                     ContractCooldownRecordRepository cooldownRecordRepository,
                                     IdempotentService idempotentService) {
        this.revokeService = revokeService;
        this.cooldownRecordRepository = cooldownRecordRepository;
        this.idempotentService = idempotentService;
    }

    /**
     * 撤销合同（冷静期内）。
     * <p>
     * 全局幂等处理：contract_id + REVOKE 操作幂等键。
     *
     * @param id      合同 ID
     * @param request 包含 revokeReason
     * @return 撤销结果
     */
    @PostMapping("/{id}/revoke")
    public ResponseEntity<Map<String, Object>> revokeContract(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        String idempotentKey = id + ":REVOKE";

        // 幂等检查：如果已撤销则直接返回成功
        String existingResult = idempotentService.getExistingResult(idempotentKey);
        if (existingResult != null) {
            log.info("合同撤销幂等命中 [contractId={}]", id);
            return ResponseEntity.ok(Map.of(
                    "code", 0,
                    "message", "合同已撤销（幂等）",
                    "contractId", id,
                    "idempotent", true
            ));
        }

        // 再次校验冷静期状态
        ContractCooldownRecord cooldownRecord = cooldownRecordRepository.findByContractId(id)
                .orElseThrow(() -> new IllegalArgumentException("冷静期记录不存在: contractId=" + id));

        if (cooldownRecord.getStatus() == CooldownStatus.REVOKED) {
            return ResponseEntity.ok(Map.of(
                    "code", 0,
                    "message", "合同已撤销",
                    "contractId", id,
                    "idempotent", true
            ));
        }

        String reason = request.getOrDefault("revokeReason", "用户主动撤销");

        log.info("合同撤销请求 [contractId={}, reason={}]", id, reason);

        RevokeResult result = revokeService.revokeContract(id, reason);

        // 记录幂等结果
        idempotentService.recordResult(idempotentKey, "REVOKED");

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", result.message(),
                "contractId", result.contractId(),
                "contractNo", result.contractNo(),
                "success", result.success(),
                "reason", result.reason()
        ));
    }
}

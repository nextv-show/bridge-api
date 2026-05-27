package com.sanshuiyuan.ess.controller;

import com.sanshuiyuan.ess.service.ContractSigningService;
import com.sanshuiyuan.ess.service.SignStatusSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * T23.8-T23.9/T23.12: 小程序/App 签署回调处理
 */
@Slf4j
@RestController
@RequestMapping("/api/contracts/callback")
@RequiredArgsConstructor
public class MiniAppCallbackController {

    private final SignStatusSyncService signStatusSyncService;

    @PostMapping("/mini/{contractId}")
    public ResponseEntity<Void> miniCallback(
            @PathVariable Long contractId,
            @RequestParam String flowId,
            @RequestParam String signResult) {
        log.info("小程序签署回调: contractId={}, flowId={}, result={}", contractId, flowId, signResult);
        signStatusSyncService.syncFromRemote(contractId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/app/{contractId}")
    public ResponseEntity<Void> appCallback(
            @PathVariable Long contractId,
            @RequestParam String flowId,
            @RequestParam String signResult) {
        log.info("App签署回调(deep link): contractId={}, flowId={}, result={}", contractId, flowId, signResult);
        signStatusSyncService.syncFromRemote(contractId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/app/{contractId}/redirect")
    public String appRedirect(@PathVariable Long contractId,
                               @RequestParam(required = false) String status) {
        return "redirect:///contract/" + contractId + "?status=" + status;
    }
}

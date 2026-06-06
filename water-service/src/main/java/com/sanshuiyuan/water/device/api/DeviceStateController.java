package com.sanshuiyuan.water.device.api;

import com.sanshuiyuan.water.common.ApiResponse;
import com.sanshuiyuan.water.common.H5UserResolver;
import com.sanshuiyuan.water.device.domain.DevicePermission;
import com.sanshuiyuan.water.device.infra.DevicePermissionRepository;
import com.sanshuiyuan.water.session.application.PriceTierGateway;
import com.sanshuiyuan.water.wallet.infra.ConsumerWalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * C 端设备状态查询。综合出水权限/在线/单价/余额，给出 dispense_block_reason 供前端置灰出水按钮。
 */
@RestController
@RequestMapping("/api/w/devices")
public class DeviceStateController {

    private static final Logger log = LoggerFactory.getLogger(DeviceStateController.class);

    private final DevicePermissionRepository permRepo;
    private final PriceTierGateway priceTierGateway;
    private final ConsumerWalletRepository walletRepo;
    private final H5UserResolver userResolver;
    private final long minStartAmountCents;

    public DeviceStateController(DevicePermissionRepository permRepo, PriceTierGateway priceTierGateway,
                                ConsumerWalletRepository walletRepo, H5UserResolver userResolver,
                                @Value("${water.min-start-amount-cents:50}") long minStartAmountCents) {
        this.permRepo = permRepo;
        this.priceTierGateway = priceTierGateway;
        this.walletRepo = walletRepo;
        this.userResolver = userResolver;
        this.minStartAmountCents = minStartAmountCents;
    }

    @GetMapping("/{sn}/state")
    public Map<String, Object> getDeviceState(Principal principal, @PathVariable String sn) {
        DevicePermission perm = permRepo.findBySn(sn).orElse(null);
        boolean canDispense = perm != null && perm.isCanDispense();
        String lockedReason = perm == null ? "NOT_INSTALLED"
                : (perm.getLockedReason() == null ? null : perm.getLockedReason().name());

        // V1 stub：设备在线默认 true
        boolean online = true;

        Long userId = userResolver.resolveUserId(principal.getName());
        long balance = walletRepo.findByUserId(userId).map(w -> w.getBalanceCents()).orElse(0L);
        int price = priceTierGateway.getPricePerLiterCents(sn);

        String blockReason = resolveBlockReason(perm, canDispense, online, balance);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sn", sn);
        data.put("online", online);
        data.put("can_dispense", canDispense);
        data.put("locked_reason", lockedReason);
        data.put("dispense_block_reason", blockReason);
        data.put("price_per_liter_cents", price);
        data.put("current_balance_cents", balance);
        return ApiResponse.ok(data);
    }

    /** 综合判断出水阻塞原因：未安装/锁定/离线/余额不足，均通过返回 null。 */
    private String resolveBlockReason(DevicePermission perm, boolean canDispense, boolean online, long balance) {
        if (perm == null) {
            return "NOT_INSTALLED";
        }
        if (!canDispense) {
            return "LOCKED";
        }
        if (!online) {
            return "OFFLINE";
        }
        if (balance < minStartAmountCents) {
            return "LOW_BALANCE";
        }
        return null;
    }
}

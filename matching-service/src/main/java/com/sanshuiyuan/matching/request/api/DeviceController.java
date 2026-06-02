package com.sanshuiyuan.matching.request.api;

import com.sanshuiyuan.matching.auth.CurrentUser;
import com.sanshuiyuan.matching.request.api.dto.LockStatusResponse;
import com.sanshuiyuan.matching.request.api.dto.MyDeviceItem;
import com.sanshuiyuan.matching.request.application.DeviceQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** C.4：接单设备选择器（PENDING_MATCH 列表）与锁定计数。 */
@RestController
@RequestMapping("/api/matching/devices")
public class DeviceController {

    private final DeviceQueryService deviceQueryService;

    public DeviceController(DeviceQueryService deviceQueryService) {
        this.deviceQueryService = deviceQueryService;
    }

    @GetMapping("/my-pending-match")
    public List<MyDeviceItem> myPendingMatch() {
        return deviceQueryService.myPendingMatch(CurrentUser.subject());
    }

    @GetMapping("/lock-status")
    public LockStatusResponse lockStatus() {
        return deviceQueryService.lockStatus(CurrentUser.subject());
    }
}

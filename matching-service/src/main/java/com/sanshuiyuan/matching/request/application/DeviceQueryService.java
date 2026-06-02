package com.sanshuiyuan.matching.request.application;

import com.sanshuiyuan.matching.assignment.infra.MatchingAssignmentRepository;
import com.sanshuiyuan.matching.identity.MatchingUserResolver;
import com.sanshuiyuan.matching.request.api.dto.LockStatusResponse;
import com.sanshuiyuan.matching.request.api.dto.MyDeviceItem;
import com.sanshuiyuan.matching.request.infra.DeviceAssetGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * C.4：接单设备选择器与锁定计数查询。
 * 均为只读路径，故用 {@link MatchingUserResolver#findUserId} 只读解析（不懒创建 users 行）：
 * 未建过 users 行的用户必然无 device_assets / 无活跃占用，直接回空。
 */
@Service
public class DeviceQueryService {

    private final MatchingUserResolver userResolver;
    private final DeviceAssetGateway deviceAssetGateway;
    private final MatchingAssignmentRepository assignmentRepository;
    private final MatchingConfigService configService;

    public DeviceQueryService(MatchingUserResolver userResolver,
                              DeviceAssetGateway deviceAssetGateway,
                              MatchingAssignmentRepository assignmentRepository,
                              MatchingConfigService configService) {
        this.userResolver = userResolver;
        this.deviceAssetGateway = deviceAssetGateway;
        this.assignmentRepository = assignmentRepository;
        this.configService = configService;
    }

    /** 当前用户名下 PENDING_MATCH 设备列表（接单时可选）。无 users 行 → 空列表。 */
    @Transactional(readOnly = true)
    public List<MyDeviceItem> myPendingMatch(String subject) {
        Optional<Long> userId = userResolver.findUserId(subject);
        if (userId.isEmpty()) {
            return List.of();
        }
        return deviceAssetGateway.findPendingMatchByOwner(userId.get()).stream()
                .map(d -> new MyDeviceItem(d.id(), d.sn(), d.stage()))
                .toList();
    }

    /** 接单锁定计数：locked=活跃占用数（与接单上限校验同口径），max=每 owner 上限。 */
    @Transactional(readOnly = true)
    public LockStatusResponse lockStatus(String subject) {
        long locked = userResolver.findUserId(subject)
                .map(assignmentRepository::countByOwnerUserIdAndReleasedAtIsNull)
                .orElse(0L);
        return new LockStatusResponse(locked, configService.lockMaxPerOwner());
    }
}

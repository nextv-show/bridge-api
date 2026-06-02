package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.api.dto.OrderCreateResponse;
import com.sanshuiyuan.cend.checkout.domain.DeviceSpec;
import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.KycRecord;
import com.sanshuiyuan.cend.checkout.domain.KycStatus;
import com.sanshuiyuan.cend.checkout.domain.OrderStatus;
import com.sanshuiyuan.cend.checkout.infra.repository.DeviceSpecRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.KycRecordRepository;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import com.sanshuiyuan.cend.referral.CendUserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CreateOrderUseCase {

    private final CendOrderRepository orderRepo;
    private final DeviceSpecRepository specRepo;
    private final KycRecordRepository kycRepo;
    private final CendUserRepository userRepo;
    private final AdminOrderProjector adminOrderProjector;

    public CreateOrderUseCase(CendOrderRepository orderRepo, DeviceSpecRepository specRepo,
                               KycRecordRepository kycRepo, CendUserRepository userRepo,
                               AdminOrderProjector adminOrderProjector) {
        this.orderRepo = orderRepo;
        this.specRepo = specRepo;
        this.kycRepo = kycRepo;
        this.userRepo = userRepo;
        this.adminOrderProjector = adminOrderProjector;
    }

    public OrderCreateResponse execute(String openid, String specId, String paymentChannel) {
        // Verify KYC
        kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc(openid, KycStatus.PASS)
                .orElseThrow(() -> new BizException(ErrorCode.KYC_REQUIRED));

        // Resolve spec (server-side price, never trust frontend)
        DeviceSpec spec = specRepo.findBySpecIdAndStatus(specId, DeviceSpec.SpecStatus.ACTIVE)
                .orElseThrow(() -> new BizException(ErrorCode.SPEC_NOT_FOUND));

        // Reuse pending order within 30min (ASSUMPTION-Q4)
        LocalDateTime thirtyMinAgo = LocalDateTime.now().minusMinutes(30);
        var existing = orderRepo.findFirstByOpenidAndSpecIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                openid, specId, OrderStatus.PENDING_PAY, thirtyMinAgo);
        if (existing.isPresent()) {
            CendOrder o = existing.get();
            return new OrderCreateResponse(o.getId(), o.getOrderNo(), o.getAmountCents(),
                    o.getSpecId(), o.getModelCode(), o.getStatus().name());
        }

        // Create new order
        String orderNo = "H5" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        CendOrder order = CendOrder.create(orderNo, openid, specId, spec.getModelCode(),
                spec.getPriceCents(), paymentChannel);

        // 下单时刻快照当前用户的 L1/L2 关系链（仅一次性写入，绝不向上递归追溯）。
        // 自然流量用户（无 h5_users 记录或未绑定关系链）快照列保持 null。
        userRepo.findByOpenid(openid).ifPresent(u ->
                order.snapshotReferral(u.getInviterId(), u.getGrandInviterId()));

        orderRepo.save(order);

        // 双写：投影到 admin orders 表（按 h5_order_no 幂等），投影失败不影响下单。
        adminOrderProjector.project(order);

        return new OrderCreateResponse(order.getId(), order.getOrderNo(), order.getAmountCents(),
                order.getSpecId(), order.getModelCode(), order.getStatus().name());
    }
}

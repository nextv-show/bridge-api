package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.OrderCreateResponse;
import com.sanshuiyuan.h5.checkout.domain.DeviceSpec;
import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.KycRecord;
import com.sanshuiyuan.h5.checkout.domain.KycStatus;
import com.sanshuiyuan.h5.checkout.domain.OrderStatus;
import com.sanshuiyuan.h5.checkout.infra.repository.DeviceSpecRepository;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.repository.KycRecordRepository;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CreateOrderUseCase {

    private final H5OrderRepository orderRepo;
    private final DeviceSpecRepository specRepo;
    private final KycRecordRepository kycRepo;

    public CreateOrderUseCase(H5OrderRepository orderRepo, DeviceSpecRepository specRepo,
                               KycRecordRepository kycRepo) {
        this.orderRepo = orderRepo;
        this.specRepo = specRepo;
        this.kycRepo = kycRepo;
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
            H5Order o = existing.get();
            return new OrderCreateResponse(o.getId(), o.getOrderNo(), o.getAmountCents(),
                    o.getSpecId(), o.getModelCode(), o.getStatus().name());
        }

        // Create new order
        String orderNo = "H5" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        H5Order order = H5Order.create(orderNo, openid, specId, spec.getModelCode(),
                spec.getPriceCents(), paymentChannel);
        orderRepo.save(order);

        return new OrderCreateResponse(order.getId(), order.getOrderNo(), order.getAmountCents(),
                order.getSpecId(), order.getModelCode(), order.getStatus().name());
    }
}

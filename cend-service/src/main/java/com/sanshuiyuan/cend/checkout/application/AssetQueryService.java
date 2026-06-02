package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.api.dto.AssetDto;
import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.Invoice;
import com.sanshuiyuan.cend.checkout.domain.InvoiceStatus;
import com.sanshuiyuan.cend.checkout.domain.OrderStatus;
import com.sanshuiyuan.cend.checkout.infra.repository.DeviceSpecRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.InvoiceRepository;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Service
public class AssetQueryService {

    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final CendOrderRepository orderRepo;
    private final DeviceSpecRepository specRepo;
    private final InvoiceRepository invoiceRepo;

    public AssetQueryService(CendOrderRepository orderRepo,
                             DeviceSpecRepository specRepo,
                             InvoiceRepository invoiceRepo) {
        this.orderRepo = orderRepo;
        this.specRepo = specRepo;
        this.invoiceRepo = invoiceRepo;
    }

    public AssetDto queryAsset(Long orderId, String openid) {
        CendOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getOpenid().equals(openid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }

        String modelName = specRepo.findBySpecId(order.getSpecId())
                .map(spec -> spec.getName())
                .orElse(order.getModelCode());

        InvoiceStatus invoiceStatus = invoiceRepo.findByOrderId(orderId)
                .map(Invoice::getStatus)
                .orElse(InvoiceStatus.ISSUING);

        long remainingSeconds = 0;
        if (order.getStatus() == OrderStatus.PAID && order.getCooldownEndAt() != null) {
            remainingSeconds = Math.max(0,
                    ChronoUnit.SECONDS.between(LocalDateTime.now(), order.getCooldownEndAt()));
        }

        String cooldownEndAtStr = null;
        if (order.getCooldownEndAt() != null) {
            cooldownEndAtStr = order.getCooldownEndAt()
                    .atZone(ZoneId.systemDefault())
                    .format(ISO_FMT);
        }

        return new AssetDto(
                order.getOrderNo(),
                modelName,
                order.getAmountCents(),
                order.getPaymentChannel(),
                cooldownEndAtStr,
                order.getStatus().name(),
                order.getSn(),
                invoiceStatus.name().toLowerCase(),
                remainingSeconds
        );
    }
}

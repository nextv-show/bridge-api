package com.sanshuiyuan.cend.checkout.application;

import com.sanshuiyuan.cend.checkout.api.dto.InvoiceDto;
import com.sanshuiyuan.cend.checkout.domain.CendOrder;
import com.sanshuiyuan.cend.checkout.domain.Invoice;
import com.sanshuiyuan.cend.checkout.infra.repository.CendOrderRepository;
import com.sanshuiyuan.cend.checkout.infra.repository.InvoiceRepository;
import com.sanshuiyuan.cend.common.BizException;
import com.sanshuiyuan.cend.common.ErrorCode;
import com.sanshuiyuan.cend.identity.IdentityResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepo;
    private final CendOrderRepository orderRepo;
    private final IdentityResolver identityResolver;

    public InvoiceService(InvoiceRepository invoiceRepo, CendOrderRepository orderRepo,
                          IdentityResolver identityResolver) {
        this.invoiceRepo = invoiceRepo;
        this.orderRepo = orderRepo;
        this.identityResolver = identityResolver;
    }

    @Transactional
    public InvoiceDto getInvoice(Long orderId, String openid) {
        CendOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND));

        // 读路径按自然人聚合：放行同人跨端查看/获取发票。
        if (!identityResolver.owns(openid, order.getOpenid())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }

        Invoice invoice = invoiceRepo.findByOrderId(orderId)
                .orElseGet(() -> {
                    Invoice inv = Invoice.createForOrder(orderId);
                    return invoiceRepo.save(inv);
                });

        return new InvoiceDto(
                invoice.getStatus().name().toLowerCase(),
                invoice.getDownloadUrl()
        );
    }
}

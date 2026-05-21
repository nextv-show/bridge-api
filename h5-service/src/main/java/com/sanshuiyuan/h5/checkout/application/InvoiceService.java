package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.InvoiceDto;
import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.Invoice;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.repository.InvoiceRepository;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepo;
    private final H5OrderRepository orderRepo;

    public InvoiceService(InvoiceRepository invoiceRepo, H5OrderRepository orderRepo) {
        this.invoiceRepo = invoiceRepo;
        this.orderRepo = orderRepo;
    }

    @Transactional
    public InvoiceDto getInvoice(Long orderId, String openid) {
        H5Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getOpenid().equals(openid)) {
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

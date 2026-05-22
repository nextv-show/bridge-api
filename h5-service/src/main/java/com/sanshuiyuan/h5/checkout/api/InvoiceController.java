package com.sanshuiyuan.h5.checkout.api;

import com.sanshuiyuan.h5.checkout.api.dto.InvoiceDto;
import com.sanshuiyuan.h5.checkout.application.InvoiceService;
import com.sanshuiyuan.h5.auth.CurrentOpenid;
import com.sanshuiyuan.h5.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/h5/order")
@Tag(name = "Invoice")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/{id}/invoice")
    @Operation(summary = "查询订单发票状态")
    public ApiResponse<InvoiceDto> getInvoice(@PathVariable Long id) {
        String openid = CurrentOpenid.require();
        return ApiResponse.ok(invoiceService.getInvoice(id, openid));
    }
}

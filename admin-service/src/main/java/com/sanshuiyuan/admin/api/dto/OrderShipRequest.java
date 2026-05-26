package com.sanshuiyuan.admin.api.dto;

public record OrderShipRequest(
        String shippingNo,
        String note
) {}

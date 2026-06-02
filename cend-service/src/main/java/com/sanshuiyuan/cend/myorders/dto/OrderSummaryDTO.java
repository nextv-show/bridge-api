package com.sanshuiyuan.cend.myorders.dto;

import java.time.LocalDateTime;

public class OrderSummaryDTO {

    private Long orderId;
    private String orderNo;
    private String snMasked;       // SN 脱敏，接口层不返回明文
    private String modelCode;
    private String modelName;
    private Long paidAmountCents;
    private String status;
    private LocalDateTime cooldownEndAt;
    private String payChannel;
    private LocalDateTime createdAt;

    private OrderSummaryDTO() {}

    public static OrderSummaryDTO of(Long orderId, String orderNo, String sn,
                                     String modelCode, String modelName,
                                     Long paidAmountCents, String status,
                                     LocalDateTime cooldownEndAt, String payChannel,
                                     LocalDateTime createdAt) {
        OrderSummaryDTO dto = new OrderSummaryDTO();
        dto.orderId = orderId;
        dto.orderNo = orderNo;
        dto.snMasked = maskSn(sn);
        dto.modelCode = modelCode;
        dto.modelName = modelName;
        dto.paidAmountCents = paidAmountCents;
        dto.status = status;
        dto.cooldownEndAt = cooldownEndAt;
        dto.payChannel = payChannel;
        dto.createdAt = createdAt;
        return dto;
    }

    /**
     * SN 脱敏：保留首 3 字符 + 末 4 字符，中间替换为 ****。
     * 占位 SN（SN-PENDING-xxx）返回「待分配」。
     */
    static String maskSn(String sn) {
        if (sn == null || sn.isBlank()) return "待分配";
        if (sn.startsWith("SN-PENDING")) return "待分配";
        if (sn.length() <= 7) return sn;  // 太短则不脱敏

        // 找最后一个 - 分隔符，保留 PREFIX-****-SUFFIX 格式
        int lastDash = sn.lastIndexOf('-');
        int firstDash = sn.indexOf('-');
        if (lastDash > firstDash && lastDash < sn.length() - 1) {
            String prefix = sn.substring(0, firstDash + 4); // PREFIX 取首段后3字符
            String suffix = sn.substring(lastDash);         // -SUFFIX
            return prefix + "-****" + suffix;
        }
        // 无分隔符时：首3 + **** + 末4
        return sn.substring(0, 3) + "****" + sn.substring(sn.length() - 4);
    }

    public Long getOrderId() { return orderId; }
    public String getOrderNo() { return orderNo; }
    public String getSnMasked() { return snMasked; }
    public String getModelCode() { return modelCode; }
    public String getModelName() { return modelName; }
    public Long getPaidAmountCents() { return paidAmountCents; }
    public String getStatus() { return status; }
    public LocalDateTime getCooldownEndAt() { return cooldownEndAt; }
    public String getPayChannel() { return payChannel; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

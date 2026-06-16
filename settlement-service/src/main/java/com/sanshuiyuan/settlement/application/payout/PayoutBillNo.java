package com.sanshuiyuan.settlement.application.payout;

/**
 * 商户转账单号（out_bill_no）。微信「商家转账」要求：仅数字/大小写字母、商户系统内唯一、
 * 长度 6–32（小 ID 直接 {@code W1S1} 仅 4 字符会被拒：字符数 4 < 最小值）。
 * 故对 orderId / splitId 做定宽零填充：{@code W<orderId,10位>S<splitId,6位>}，
 * 既满足最小长度又保持确定性可重建（便于查单/对账幂等）。
 */
public final class PayoutBillNo {
    private PayoutBillNo() {}

    public static String of(long orderId, long splitId) {
        return String.format("W%010dS%06d", orderId, splitId);
    }
}

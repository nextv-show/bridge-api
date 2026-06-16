package com.sanshuiyuan.settlement.application.payout;

/**
 * 商户转账单号（out_bill_no）。微信要求仅数字/大小写字母、商户系统内唯一。
 * 用 {@code W<orderId>S<splitId>}（确定性可重建，便于查单/对账幂等）。
 */
public final class PayoutBillNo {
    private PayoutBillNo() {}

    public static String of(long orderId, long splitId) {
        return "W" + orderId + "S" + splitId;
    }
}

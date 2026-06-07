package com.sanshuiyuan.settlement.infra.wxpay;

import com.sanshuiyuan.settlement.domain.WithdrawalSplit;

/**
 * 微信商家转账到零钱 V3 API。V1 先用 Stub 实现。
 */
public interface WxMchPayoutClient {

    record PayoutResult(boolean accepted, String batchId, String detailId, String errorCode) {
        public static PayoutResult ok(String batchId, String detailId) {
            return new PayoutResult(true, batchId, detailId, null);
        }
        public static PayoutResult failed(String errorCode) {
            return new PayoutResult(false, null, null, errorCode);
        }
    }

    /**
     * 发起单笔商家转账。
     * @param split 提现拆分记录（含 orderId, amountCents, user 的 openid 等）
     * @return 转账结果
     */
    PayoutResult transfer(WithdrawalSplit split);
}

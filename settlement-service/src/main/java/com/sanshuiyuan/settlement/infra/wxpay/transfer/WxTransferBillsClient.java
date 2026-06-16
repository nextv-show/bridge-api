package com.sanshuiyuan.settlement.infra.wxpay.transfer;

/**
 * 微信「商家转账」V3（2025-01-15 新版 /v3/fund-app/mch-transfer/transfer-bills，用户确认收款模式）。
 * 后端建转账单 → 返回 package_info（state=WAIT_USER_CONFIRM 时）→ 小程序 wx.requestMerchantTransfer 确认。
 * 最终态以 notify 回调 / 主动查单为准。
 */
public interface WxTransferBillsClient {

    /** 发起转账入参。userName 仅 ≥¥2000 需要（加密真实姓名）；V1 限额 <¥2000，userName 传 null。 */
    record TransferCommand(String outBillNo, String openid, long amountCents,
                           String transferRemark, String userName) {}

    /** 发起结果。state ∈ {ACCEPTED,PROCESSING,WAIT_USER_CONFIRM,TRANSFERING,SUCCESS,FAIL,CANCELLED}。 */
    record InitiateResult(boolean accepted, String outBillNo, String transferBillNo,
                          String state, String packageInfo, String errorCode, String errorMessage) {
        public static InitiateResult ok(String outBillNo, String transferBillNo, String state, String packageInfo) {
            return new InitiateResult(true, outBillNo, transferBillNo, state, packageInfo, null, null);
        }
        public static InitiateResult failed(String errorCode, String errorMessage) {
            return new InitiateResult(false, null, null, null, null, errorCode, errorMessage);
        }
    }

    /** 查单结果（兜底对账）。 */
    record QueryResult(boolean found, String state, String failReason, String transferBillNo) {
        public static QueryResult notFound() { return new QueryResult(false, null, null, null); }
        public static QueryResult error(String reason) { return new QueryResult(false, null, reason, null); }
    }

    /** 发起转账单（POST transfer-bills）。 */
    InitiateResult initiate(TransferCommand cmd);

    /** 按商户单号查单（GET transfer-bills/out-bill-no/{outBillNo}）。 */
    QueryResult queryByOutBillNo(String outBillNo);
}

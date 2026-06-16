package com.sanshuiyuan.settlement.infra.wxpay.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 未配置商户转账凭证时的回退实现：不真正转账。
 * 返回 WAIT_USER_CONFIRM + 占位 package_info，使上层流程在 dev / 未配置环境可走通而不动真钱。
 */
public class StubWxTransferBillsClient implements WxTransferBillsClient {

    private static final Logger log = LoggerFactory.getLogger(StubWxTransferBillsClient.class);

    @Override
    public InitiateResult initiate(TransferCommand cmd) {
        log.info("[stub] transfer-bills initiate outBillNo={} amount={} （未配置真实凭证，不动真钱）",
                cmd.outBillNo(), cmd.amountCents());
        return InitiateResult.ok(cmd.outBillNo(), "STUB_BILL_" + cmd.outBillNo(),
                "WAIT_USER_CONFIRM", "STUB_PACKAGE_INFO_" + cmd.outBillNo());
    }

    @Override
    public QueryResult queryByOutBillNo(String outBillNo) {
        return new QueryResult(true, "SUCCESS", null, "STUB_BILL_" + outBillNo);
    }
}

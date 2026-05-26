package com.sanshuiyuan.asset.infra.wxpay;

/** 已验签+解密的钱包充值支付回调。outTradeNo 形如 "WR{rechargeId}"。 */
public record WalletCallbackResult(String transactionId, String outTradeNo, String tradeState) {}

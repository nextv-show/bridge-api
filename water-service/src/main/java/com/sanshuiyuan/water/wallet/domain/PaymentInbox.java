package com.sanshuiyuan.water.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 支付回调收件箱。以微信交易号唯一键实现回调幂等：同一笔交易重复回调只落账一次。
 */
@Entity
@Table(name = "payment_inbox")
public class PaymentInbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 64)
    private String transactionId;

    @Column(name = "out_trade_no", length = 40)
    private String outTradeNo;

    @Column(name = "raw_body")
    private String rawBody;

    @Column(name = "processed_at", insertable = false, updatable = false)
    private LocalDateTime processedAt;

    protected PaymentInbox() {
    }

    public static PaymentInbox create(String transactionId, String outTradeNo, String rawBody) {
        PaymentInbox p = new PaymentInbox();
        p.transactionId = transactionId;
        p.outTradeNo = outTradeNo;
        p.rawBody = rawBody;
        return p;
    }

    public Long getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public String getOutTradeNo() { return outTradeNo; }
    public String getRawBody() { return rawBody; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}

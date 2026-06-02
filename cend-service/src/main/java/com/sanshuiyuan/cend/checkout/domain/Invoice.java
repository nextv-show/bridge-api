package com.sanshuiyuan.cend.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvoiceStatus status;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "download_url", length = 512)
    private String downloadUrl;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected Invoice() {
    }

    public static Invoice createForOrder(Long orderId) {
        Invoice inv = new Invoice();
        inv.orderId = orderId;
        inv.status = InvoiceStatus.ISSUING;
        inv.type = "VAT_SPECIAL_13";
        return inv;
    }

    public void markIssued(String downloadUrl) {
        this.status = InvoiceStatus.ISSUED;
        this.downloadUrl = downloadUrl;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public InvoiceStatus getStatus() { return status; }
    public String getType() { return type; }
    public String getDownloadUrl() { return downloadUrl; }
}

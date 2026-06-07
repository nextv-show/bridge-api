package com.sanshuiyuan.settlement.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 分账分录：一笔账单按受益人维度拆分后的结算明细。 */
@Entity
@Table(name = "settlement_entries")
public class SettlementEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(nullable = false)
    private String sn;

    @Enumerated(EnumType.STRING)
    @Column(name = "beneficiary_type", nullable = false)
    private BeneficiaryType beneficiaryType;

    @Column(name = "beneficiary_user_id")
    private Long beneficiaryUserId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "owner_bp", nullable = false)
    private Integer ownerBp;

    @Column(name = "promoter_bp", nullable = false)
    private Integer promoterBp;

    @Column(name = "platform_bp", nullable = false)
    private Integer platformBp;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_reason", nullable = false)
    private SettlementSplitReason splitReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_at_post", nullable = false)
    private DeviceStage stageAtPost;

    @Column(name = "posted_at", insertable = false, updatable = false)
    private LocalDateTime postedAt;

    protected SettlementEntry() {}

    public SettlementEntry(Long billId, String sn, BeneficiaryType beneficiaryType, Long beneficiaryUserId,
                           Long amountCents, Integer ownerBp, Integer promoterBp, Integer platformBp,
                           SettlementSplitReason splitReason, DeviceStage stageAtPost) {
        this.billId = billId;
        this.sn = sn;
        this.beneficiaryType = beneficiaryType;
        this.beneficiaryUserId = beneficiaryUserId;
        this.amountCents = amountCents;
        this.ownerBp = ownerBp;
        this.promoterBp = promoterBp;
        this.platformBp = platformBp;
        this.splitReason = splitReason;
        this.stageAtPost = stageAtPost;
    }

    public Long getId() { return id; }
    public Long getBillId() { return billId; }
    public void setBillId(Long billId) { this.billId = billId; }
    public String getSn() { return sn; }
    public void setSn(String sn) { this.sn = sn; }
    public BeneficiaryType getBeneficiaryType() { return beneficiaryType; }
    public void setBeneficiaryType(BeneficiaryType beneficiaryType) { this.beneficiaryType = beneficiaryType; }
    public Long getBeneficiaryUserId() { return beneficiaryUserId; }
    public void setBeneficiaryUserId(Long beneficiaryUserId) { this.beneficiaryUserId = beneficiaryUserId; }
    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }
    public Integer getOwnerBp() { return ownerBp; }
    public void setOwnerBp(Integer ownerBp) { this.ownerBp = ownerBp; }
    public Integer getPromoterBp() { return promoterBp; }
    public void setPromoterBp(Integer promoterBp) { this.promoterBp = promoterBp; }
    public Integer getPlatformBp() { return platformBp; }
    public void setPlatformBp(Integer platformBp) { this.platformBp = platformBp; }
    public SettlementSplitReason getSplitReason() { return splitReason; }
    public void setSplitReason(SettlementSplitReason splitReason) { this.splitReason = splitReason; }
    public DeviceStage getStageAtPost() { return stageAtPost; }
    public void setStageAtPost(DeviceStage stageAtPost) { this.stageAtPost = stageAtPost; }
    public LocalDateTime getPostedAt() { return postedAt; }
}

package com.sanshuiyuan.settlement.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 收件箱游标：记录每个来源（如 water_bill）已消费到的最大 id，用于增量轮询拉取。 */
@Entity
@Table(name = "settlement_inbox_cursor")
public class SettlementInboxCursor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;  // "water_bill"

    @Column(name = "last_id", nullable = false)
    private Long lastId = 0L;

    // DB 默认 CURRENT_TIMESTAMP + ON UPDATE CURRENT_TIMESTAMP 维护，应用不写。
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected SettlementInboxCursor() {}

    public SettlementInboxCursor(String name) {
        this.name = name;
        this.lastId = 0L;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getLastId() { return lastId; }
    public void setLastId(Long lastId) { this.lastId = lastId; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

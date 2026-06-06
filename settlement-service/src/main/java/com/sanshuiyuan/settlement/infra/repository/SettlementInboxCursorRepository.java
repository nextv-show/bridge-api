package com.sanshuiyuan.settlement.infra.repository;

import com.sanshuiyuan.settlement.domain.SettlementInboxCursor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementInboxCursorRepository extends JpaRepository<SettlementInboxCursor, Long> {
    Optional<SettlementInboxCursor> findByName(String name);
}

package com.sanshuiyuan.asset.api.dto;

import com.sanshuiyuan.asset.domain.Stage;
import java.time.LocalDateTime;

public record AssetDto(
    Long id,
    String sn,
    String model,
    LocalDateTime purchasedAt,
    Stage stage,
    Long cumulativeIncomeCents,
    Integer roiBp,
    boolean pendingMatch,
    boolean fused
) {}

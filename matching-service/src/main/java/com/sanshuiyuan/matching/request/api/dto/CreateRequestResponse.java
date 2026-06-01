package com.sanshuiyuan.matching.request.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record CreateRequestResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("status") String status,
        @JsonProperty("created_at") LocalDateTime createdAt
) {}

package com.sanshuiyuan.matching.request.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** P1-2 确认推进响应。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ConfirmResponse(
        @JsonProperty("request_id") long requestId,
        @JsonProperty("status") String status,
        @JsonProperty("claim_confirmed_at") LocalDateTime claimConfirmedAt
) {}

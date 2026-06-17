package com.sanshuiyuan.matching.request.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClaimRequestResponse(
        @JsonProperty("request_id") Long requestId,
        @JsonProperty("status") String status
) {}

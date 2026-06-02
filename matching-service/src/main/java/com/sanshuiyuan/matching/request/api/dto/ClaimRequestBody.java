package com.sanshuiyuan.matching.request.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClaimRequestBody(
        @JsonProperty("device_asset_id") Long deviceAssetId
) {}

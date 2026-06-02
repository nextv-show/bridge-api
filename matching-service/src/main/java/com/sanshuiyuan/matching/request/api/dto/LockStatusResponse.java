package com.sanshuiyuan.matching.request.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 当前用户接单锁定计数：locked=活跃占用数，max=每 owner 上限（matching_config）。 */
public record LockStatusResponse(
        @JsonProperty("locked") long locked,
        @JsonProperty("max") int max
) {}

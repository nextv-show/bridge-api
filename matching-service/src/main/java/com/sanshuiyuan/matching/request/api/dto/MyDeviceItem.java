package com.sanshuiyuan.matching.request.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 当前用户名下「待匹配」设备项（接单设备选择器用）。 */
public record MyDeviceItem(
        @JsonProperty("id") long id,
        @JsonProperty("sn") String sn,
        @JsonProperty("stage") String stage
) {}

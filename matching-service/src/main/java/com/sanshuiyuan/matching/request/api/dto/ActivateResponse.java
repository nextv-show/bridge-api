package com.sanshuiyuan.matching.request.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 029 设备激活响应。activated=false 表示幂等 no-op（已激活 / 无此 SN / 非 PENDING_ACTIVATE）。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ActivateResponse(
        @JsonProperty("sn") String sn,
        @JsonProperty("activated") boolean activated
) {}

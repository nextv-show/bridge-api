package com.sanshuiyuan.matching.request.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** FR-5：取消需求响应。 */
public record CancelResponse(
        @JsonProperty("request_id") long requestId,
        @JsonProperty("status") String status) {
}

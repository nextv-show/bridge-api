package com.sanshuiyuan.matching.request.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** FR-5：释放需求响应。 */
public record ReleaseResponse(
        @JsonProperty("request_id") long requestId,
        @JsonProperty("status") String status) {
}

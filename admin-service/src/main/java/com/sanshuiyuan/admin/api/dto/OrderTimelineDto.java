package com.sanshuiyuan.admin.api.dto;

public record OrderTimelineDto(
        String stage,
        String label,
        String at,
        String note
) {}

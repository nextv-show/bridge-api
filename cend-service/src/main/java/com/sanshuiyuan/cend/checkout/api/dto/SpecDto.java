package com.sanshuiyuan.cend.checkout.api.dto;

import java.util.List;

public record SpecDto(
    String specId,
    String modelCode,
    String name,
    Long priceCents,
    boolean recommended,
    String monitorLine,
    List<String> features
) {}

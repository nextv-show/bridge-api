package com.sanshuiyuan.cend.api.dto;

/**
 * 合规 Footer：固定免责声明 + 备案号。
 */
public record FooterDto(
        String disclaimer,
        String icpNumber
) {
}

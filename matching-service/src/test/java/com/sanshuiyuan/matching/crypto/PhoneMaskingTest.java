package com.sanshuiyuan.matching.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhoneMaskingTest {

    @Test
    void mask_standard() {
        assertEquals("138****5678", PhoneMasking.mask("13812345678"));
    }

    @Test
    void mask_nonElevenDigits() {
        assertEquals("***", PhoneMasking.mask("12345"));
        assertEquals("***", PhoneMasking.mask(null));
    }

    @Test
    void normalize_stripsSeparators() {
        assertEquals("13812345678", PhoneMasking.normalize(" 138-1234-5678 "));
        assertEquals("13812345678", PhoneMasking.normalize("138 1234 5678"));
        assertEquals("13812345678", PhoneMasking.normalize("(138)12345678"));
    }
}

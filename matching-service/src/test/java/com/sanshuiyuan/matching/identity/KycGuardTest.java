package com.sanshuiyuan.matching.identity;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** P1-3 实名门控判定单测。 */
class KycGuardTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final KycGuard guard = new KycGuard(jdbc);

    @Test
    void passedWhenCountPositive() {
        when(jdbc.queryForObject(any(String.class), eq(Long.class), eq("openid-1"))).thenReturn(1L);
        assertTrue(guard.hasPassedKyc("openid-1"));
    }

    @Test
    void notPassedWhenZero() {
        when(jdbc.queryForObject(any(String.class), eq(Long.class), eq("openid-2"))).thenReturn(0L);
        assertFalse(guard.hasPassedKyc("openid-2"));
    }

    @Test
    void notPassedWhenNull() {
        when(jdbc.queryForObject(any(String.class), eq(Long.class), eq("openid-3"))).thenReturn(null);
        assertFalse(guard.hasPassedKyc("openid-3"));
    }
}

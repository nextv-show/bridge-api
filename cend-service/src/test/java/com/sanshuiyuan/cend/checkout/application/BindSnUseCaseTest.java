package com.sanshuiyuan.cend.checkout.application;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 110 {@link BindSnUseCase} 纯单测（mock JdbcTemplate，不依赖 DB）：
 * 链路查询（device_assets → orders → h5_orders）、幂等回写、空值/占位符跳过。
 */
class BindSnUseCaseTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BindSnUseCase useCase = new BindSnUseCase(jdbc);

    @Test
    void placeholderToReal_updatesH5Orders() {
        when(jdbc.queryForList(contains("FROM device_assets"), eq(Long.class), eq(7L)))
                .thenReturn(List.of(500L));
        when(jdbc.queryForList(contains("FROM orders"), eq(String.class), eq(500L)))
                .thenReturn(List.of("H5ORDER001"));
        when(jdbc.update(contains("UPDATE h5_orders"), eq("SN-REAL-001"), eq("H5ORDER001")))
                .thenReturn(1);

        boolean result = useCase.tryBindSn(7L, "SN-REAL-001");

        assertThat(result).isTrue();
        verify(jdbc).update(contains("SN-PENDING-%"), eq("SN-REAL-001"), eq("H5ORDER001"));
    }

    @Test
    void alreadyRealSn_idempotentSkip_returnsFalse() {
        // UPDATE 命中 0 行（h5_orders.sn 已是真实 SN，不匹配 LIKE 'SN-PENDING-%'）
        when(jdbc.queryForList(contains("FROM device_assets"), eq(Long.class), eq(7L)))
                .thenReturn(List.of(500L));
        when(jdbc.queryForList(contains("FROM orders"), eq(String.class), eq(500L)))
                .thenReturn(List.of("H5ORDER001"));
        when(jdbc.update(contains("UPDATE h5_orders"), eq("SN-REAL-001"), eq("H5ORDER001")))
                .thenReturn(0);

        boolean result = useCase.tryBindSn(7L, "SN-REAL-001");

        assertThat(result).isFalse();
    }

    @Test
    void blankSn_skips_noQuery() {
        assertThat(useCase.tryBindSn(7L, "  ")).isFalse();
        verify(jdbc, never()).queryForList(contains("FROM device_assets"), eq(Long.class), eq(7L));
    }

    @Test
    void placeholderSn_skips_noQuery() {
        assertThat(useCase.tryBindSn(7L, "SN-PENDING-H5ORDER001")).isFalse();
        verify(jdbc, never()).queryForList(contains("FROM device_assets"), eq(Long.class), eq(7L));
    }

    @Test
    void deviceAssetNotFound_skips_noUpdate() {
        when(jdbc.queryForList(contains("FROM device_assets"), eq(Long.class), eq(7L)))
                .thenReturn(List.of());

        assertThat(useCase.tryBindSn(7L, "SN-REAL-001")).isFalse();
        verify(jdbc, never()).update(contains("UPDATE h5_orders"), eq("SN-REAL-001"), eq("H5ORDER001"));
    }

    @Test
    void orderNotFound_skips_noUpdate() {
        when(jdbc.queryForList(contains("FROM device_assets"), eq(Long.class), eq(7L)))
                .thenReturn(List.of(500L));
        when(jdbc.queryForList(contains("FROM orders"), eq(String.class), eq(500L)))
                .thenReturn(List.of());

        assertThat(useCase.tryBindSn(7L, "SN-REAL-001")).isFalse();
        verify(jdbc, never()).update(contains("UPDATE h5_orders"), eq("SN-REAL-001"), eq("H5ORDER001"));
    }
}

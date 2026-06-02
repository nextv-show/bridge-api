package com.sanshuiyuan.matching.request.infra;

import com.sanshuiyuan.matching.request.domain.DeviceStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * C.1.3 守护：{@link DeviceAssetGateway} 只能推进 device_assets.stage，其它列硬性禁写。
 * 纯单测（Mockito，不依赖 DB / Docker），断言落到 JdbcTemplate 的 SQL 与参数。
 */
@ExtendWith(MockitoExtension.class)
class DeviceAssetGatewayTest {

    @Mock
    JdbcTemplate jdbc;

    /** device_assets 中除 stage 外的列：均不得出现在网关 SQL 里。 */
    private static final String[] FORBIDDEN_COLUMNS = {
            "user_id =", "order_id", "sn", "model", "purchased_at",
            "cumulative_income_cents", "roi_bp"
    };

    @Test
    void advanceStage_issuesOnlyWhitelistedSql_andForwardsArgsInOrder() {
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        DeviceAssetGateway gateway = new DeviceAssetGateway(jdbc);

        int rows = gateway.advanceStage(42L, 7L, DeviceStage.PENDING_MATCH, DeviceStage.LOCKED);

        assertThat(rows).isEqualTo(1);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(sql.capture(), args.capture(), args.capture(), args.capture(), args.capture());
        verifyNoMoreInteractions(jdbc);

        // 1) 必须是写死的白名单语句。
        assertThat(sql.getValue()).isEqualTo(DeviceAssetGateway.UPDATE_STAGE_SQL);

        // 2) SET 子句只允许写 stage：SET 与 WHERE 之间不得出现其它列。
        String upper = sql.getValue().toUpperCase(Locale.ROOT);
        String setClause = upper.substring(upper.indexOf("SET") + 3, upper.indexOf("WHERE"));
        assertThat(setClause).contains("STAGE").doesNotContain(",");
        for (String col : FORBIDDEN_COLUMNS) {
            assertThat(setClause).doesNotContain(col.toUpperCase(Locale.ROOT).split(" ")[0]);
        }

        // 3) WHERE 必须同时校验归属(user_id)与前置态(stage)，使推进具 CAS 语义。
        assertThat(upper).contains("WHERE ID = ?").contains("USER_ID = ?").contains("STAGE = ?");

        // 4) 参数顺序：next(stage), id, user_id, expected(stage)。
        assertThat(args.getAllValues())
                .containsExactly("LOCKED", 42L, 7L, "PENDING_MATCH");
    }

    @Test
    void advanceStage_returnsZeroRows_whenGuardNotMet() {
        // 归属或前置态不符 → UPDATE 命中 0 行；网关原样返回，调用方据此判 403/409。
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(0);
        DeviceAssetGateway gateway = new DeviceAssetGateway(jdbc);

        int rows = gateway.advanceStage(1L, 999L, DeviceStage.PENDING_MATCH, DeviceStage.LOCKED);

        assertThat(rows).isZero();
    }

    @Test
    void findPendingMatchByOwner_readsIdSnStage_scopedToOwnerAndPendingMatch() {
        DeviceAssetGateway.PendingMatchDevice row =
                new DeviceAssetGateway.PendingMatchDevice(42L, "SN-001", "PENDING_MATCH");
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any()))
                .thenReturn(java.util.List.of(row));
        DeviceAssetGateway gateway = new DeviceAssetGateway(jdbc);

        var result = gateway.findPendingMatchByOwner(7L);

        assertThat(result).containsExactly(row);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).query(sql.capture(), any(org.springframework.jdbc.core.RowMapper.class),
                args.capture(), args.capture());

        // 只读三列、按 owner 归属 + PENDING_MATCH 前置态过滤。
        String upper = sql.getValue().toUpperCase(Locale.ROOT);
        assertThat(upper).contains("SELECT ID, SN, STAGE").contains("USER_ID = ?").contains("STAGE = ?");
        assertThat(args.getAllValues()).containsExactly(7L, DeviceStage.PENDING_MATCH.name());
    }
}

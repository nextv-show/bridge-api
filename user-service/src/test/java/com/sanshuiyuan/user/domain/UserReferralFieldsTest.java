package com.sanshuiyuan.user.domain;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T8b.6：确认 008a 已为 canonical User 实体补齐关系链 + 多端识别键字段，并映射到正确列名。
 * 作为回归守卫：inviter_id(L1) / grand_inviter_id(L2) / phone 任一被误删或改名即失败。
 */
class UserReferralFieldsTest {

    @Test
    void user_mapsReferralChainAndPhoneColumns() {
        assertColumn("inviterId", "inviter_id");
        assertColumn("grandInviterId", "grand_inviter_id");
        // phone 用默认列名（字段名即列名），仅断言字段与读写存在。
        assertThat(hasField("phone")).isTrue();

        User u = new User();
        u.setInviterId(100L);
        u.setGrandInviterId(50L);
        u.setPhone("13800000000");
        assertThat(u.getInviterId()).isEqualTo(100L);
        assertThat(u.getGrandInviterId()).isEqualTo(50L);
        assertThat(u.getPhone()).isEqualTo("13800000000");
    }

    private void assertColumn(String fieldName, String expectedColumn) {
        Field f = field(fieldName);
        Column col = f.getAnnotation(Column.class);
        assertThat(col).as("字段 %s 应有 @Column 映射", fieldName).isNotNull();
        assertThat(col.name()).as("字段 %s 应映射到列 %s", fieldName, expectedColumn).isEqualTo(expectedColumn);
    }

    private static boolean hasField(String name) {
        try {
            User.class.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private static Field field(String name) {
        try {
            return User.class.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("User 实体缺少字段 " + name, e);
        }
    }
}

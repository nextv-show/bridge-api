package com.sanshuiyuan.matching.request;

import com.sanshuiyuan.matching.request.domain.PriceTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceTierTest {

    @Test
    void ordering() {
        assertTrue(PriceTier.T_040.order() < PriceTier.T_080.order());
        assertTrue(PriceTier.T_080.order() < PriceTier.T_120.order());
        assertTrue(PriceTier.T_120.order() < PriceTier.T_150.order());
    }

    @Test
    void atLeast_inclusive() {
        assertTrue(PriceTier.T_120.atLeast(PriceTier.T_120));
        assertTrue(PriceTier.T_150.atLeast(PriceTier.T_040));
        assertFalse(PriceTier.T_040.atLeast(PriceTier.T_080));
    }

    @Test
    void valueOf_strict() {
        assertTrue(PriceTier.valueOf("T_040") == PriceTier.T_040);
    }
}

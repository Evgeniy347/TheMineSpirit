package com.theminespirit.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlanLimitsTests {
    @Test
    void maxDepth_AgreedLimit_IsSixteen() {
        assertEquals(
                16,
                PlanLimits.MAX_DEPTH,
                "Предельная глубина плана — 16: больше вложенность не нужна и платит тиками"
                        + " Иначе тест maxDepth_AgreedLimit_IsSixteen перестанет ловить эту поломку. При смене maxDepth_AgreedLimit_IsSixteen проверь это утверждение.");
    }

    @Test
    void maxNodes_AgreedLimit_Is256() {
        assertEquals(
                256,
                PlanLimits.MAX_NODES,
                "Предельное число узлов плана — 256: больше бюджет не окупается"
                        + " Иначе тест maxNodes_AgreedLimit_Is256 перестанет ловить эту поломку. При смене maxNodes_AgreedLimit_Is256 проверь это утверждение.");
    }
}

package com.theminespirit.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlanLimitsTests {
    @Test
    void maxDepth_AgreedLimit_IsSixteen() {
        assertEquals(16, PlanLimits.MAX_DEPTH,
                "Предельная глубина плана — 16: больше вложенность не нужна и платит тиками");
    }

    @Test
    void maxNodes_AgreedLimit_Is256() {
        assertEquals(256, PlanLimits.MAX_NODES,
                "Предельное число узлов плана — 256: больше бюджет не окупается");
    }
}

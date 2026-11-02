package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ChopTuningTests {
    @Test
    void gatherDistance_FarRequest_IsCappedToSettlementRadius() {
        assertEquals(
                ChopTuning.GATHER_RADIUS_MAX,
                ChopTuning.gatherDistance(256),
                "Сбор не уходит за радиус поселения: иначе дух идёт к брёвнам за 100+ блоков (observe19 E)");
    }

    @Test
    void gatherDistance_NearRequest_StaysAsRequested() {
        assertEquals(16, ChopTuning.gatherDistance(16), "Ближний сбор не расширяется без нужды");
    }

    @Test
    void gatherDistance_NonPositive_Throws() {
        assertThrows(IllegalArgumentException.class, () -> ChopTuning.gatherDistance(0), "Радиус 0 бессмыслен");
    }
}

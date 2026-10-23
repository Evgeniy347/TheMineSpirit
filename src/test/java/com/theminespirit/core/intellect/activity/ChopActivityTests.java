package com.theminespirit.core.intellect.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.intellect.SettlementViews;
import com.theminespirit.core.processing.ProcessingStock;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChopActivityTests {
    private static ProcessingStock logs(int oak, int birch) {
        return new ProcessingStock(
                0, 0, 0, 0, Map.of(), Map.of("minecraft:oak_log", oak, "minecraft:birch_log", birch));
    }

    @Test
    void demand_LogsAtFirstStageCap_StopsChopping() {
        ChopActivity chop = new ChopActivity();

        assertEquals(
                0.0,
                chop.demand(SettlementViews.processing(3, 0, logs(40, 24))),
                "64 бревна на первом этапе — предел (владелец 23.10): рубка останавливается, а не копит брёвна");
    }

    @Test
    void demand_LogsBelowCap_KeepsChopping() {
        ChopActivity chop = new ChopActivity();

        assertTrue(
                chop.demand(SettlementViews.processing(3, 0, logs(40, 23))) > 0,
                "Ниже предела рубка продолжается: брёвна нужны доскам, палкам и постройкам");
    }

    @Test
    void demand_HigherStageCap_KeepsChopping() {
        ChopActivity chop = new ChopActivity(() -> 128);

        assertTrue(
                chop.demand(SettlementViews.processing(3, 0, logs(64, 0))) > 0,
                "На следующем этапе предел вдвое выше: 64 бревна — ещё не предел");
    }
}

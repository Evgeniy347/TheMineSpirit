package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class HighStrayScanTests {
    private static final int LEVEL = 70;

    @Test
    void scan_HighColumnAboveHead_IsHighNotOrdinaryStray() {
        SettlementScanTests.Fake world = new SettlementScanTests.Fake();
        world.put(2, LEVEL + 20, 2, "minecraft:cobblestone");

        SettlementScan.Strays found = SettlementScanTests.scan(world, ColumnMask.rect(0, 0, 6, 6));

        assertEquals(
                List.of(new BlockCoord(2, LEVEL + 20, 2)),
                found.high().stream().map(cell -> cell.at()).toList(),
                "Столб на 20 выше земли обязан попасть в отдельный факт high_strays: с земли его не достать, "
                        + "а обычная расчистка ищет только нижние четыре клетки");
        assertEquals(List.of(), found.strays(), "Высокое не смешивается с обычным лишним");
    }
}

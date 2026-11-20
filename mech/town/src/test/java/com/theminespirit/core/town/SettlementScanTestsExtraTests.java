package com.theminespirit.core.town;

import static com.theminespirit.core.town.SettlementScanTests.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementScanTestsExtraTests {

    @Test
    void scan_PlannedDepressionsAndSpiritTrace_AreNotPits() {
        Fake world = new Fake();
        world.lot(1, 1);
        world.hole(1, 1, 4);
        for (int y = LEVEL - 4; y <= LEVEL - 1; y++) {
            world.planned.add(new BlockCoord(1, y, 1));
        }
        world.lot(3, 1);
        world.hole(3, 1, 3);
        for (int y = LEVEL - 3; y <= LEVEL - 1; y++) {
            world.planned.add(new BlockCoord(3, y, 1));
        }
        world.lot(5, 1);
        world.put(5, LEVEL - 1, 1, "minecraft:water");
        world.planned.add(new BlockCoord(5, LEVEL - 1, 1));
        world.hole(7, 1, 3);
        world.dug(7, LEVEL - 1, 1);
        for (int y = LEVEL - 3; y <= LEVEL - 1; y++) {
            world.planned.add(new BlockCoord(7, y, 1));
        }
        world.hole(9, 1, 3);
        world.dug(9, LEVEL - 1, 1);
        world.spirit.add(new BlockCoord(9, LEVEL - 2, 1));

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 12, 3));

        assertEquals(
                Map.of(),
                found.pits(),
                "Шахта, колодец, пашня с водой и ступени спуска — законные углубления плана, "
                        + "а след духа возвращает restore_trace: засыпать их ямами нельзя"
                        + " Иначе тест scan_PlannedDepressionsAndSpiritTrace_AreNotPits перестанет ловить эту поломку. При смене scan_PlannedDepressionsAndSpiritTrace_AreNotPits проверь это утверждение.");
        assertEquals(
                List.of(new BlockCoord(9, LEVEL - 2, 1)),
                found.traces(),
                "След духа остаётся следом, а не ямой: его возвращает отдельное задание"
                        + " Иначе тест scan_PlannedDepressionsAndSpiritTrace_AreNotPits перестанет ловить эту поломку. При смене scan_PlannedDepressionsAndSpiritTrace_AreNotPits проверь это утверждение.");
    }

    @Test
    void scan_SpiritDugCell_IsTrace() {
        Fake world = new Fake();
        world.put(2, LEVEL - 2, 2, "minecraft:air");
        world.spirit.add(new BlockCoord(2, LEVEL - 2, 2));

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 6, 6));

        assertEquals(
                List.of(new BlockCoord(2, LEVEL - 2, 2)),
                found.traces(),
                "яма, выкопанная духом, — след: её возвращают как было"
                        + " Иначе тест scan_SpiritDugCell_IsTrace перестанет ловить эту поломку. При смене scan_SpiritDugCell_IsTrace проверь это утверждение.");
    }
}

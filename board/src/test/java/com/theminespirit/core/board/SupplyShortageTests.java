package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupplyShortageTests {
    @Test
    void shortages_AbsentItem_BlockForGraphNotGatherTicket() {
        SupplyGate.Decision decision = SupplyGate.decide(
                Map.of("minecraft:crafting_table", 1, "minecraft:dirt", 4),
                Map.of("minecraft:dirt", 10),
                0,
                Map.of(),
                0,
                0);

        List<GraphBlock> blocks = SupplyGate.shortages(decision);

        assertEquals(
                List.of(new GraphBlock(
                        "shortage:minecraft:crafting_table", "", new GraphBlock.Item("crafting_table", 1))),
                blocks,
                "Нет предмета нигде — нехватка уходит в граф блокировкой (граф решает: скрафтить или добыть), "
                        + "а не задачей «добыть» мимо графа; то, что лежит в сундуках, несёт снабжение");
    }
}

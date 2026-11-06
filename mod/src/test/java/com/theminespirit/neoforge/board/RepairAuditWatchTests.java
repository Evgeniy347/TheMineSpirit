package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.Schematic;
import org.junit.jupiter.api.Test;

class RepairAuditWatchTests {
    @Test
    void houseSchematic_JungleWood_SwapsPlanks() {
        Schematic oak = RepairAuditWatch.wanted("hut_1", "oak");
        Schematic jungle = RepairAuditWatch.wanted("hut_1", "jungle");

        assertTrue(
                oak.blocks().stream().anyMatch(entry -> entry.block().contains("oak_planks")),
                "Дубовая схема дома содержит дубовые доски — основа для сверки");
        assertTrue(
                jungle.blocks().stream().anyMatch(entry -> entry.block().contains("jungle_planks")),
                "Аудит дома обязан брать породу поселения: иначе /v1/world считает дом повреждённым (45), "
                        + "пока сборочный аудит того же дома даёт 100/100");
        assertFalse(
                jungle.blocks().stream().anyMatch(entry -> entry.block().contains("oak_planks")),
                "В джунглевой схеме не остаётся дубовых досок: один источник правды о материале дома");
    }
}

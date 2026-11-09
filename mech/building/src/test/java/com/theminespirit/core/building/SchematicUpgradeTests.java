package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchematicUpgradeTests {
    private static final BlockCoord ORIGIN = new BlockCoord(10, 70, -5);

    @Test
    void extraPositions_BlocksMissingFromNextScheme_AreRemoved() {
        List<BlockCoord> previous =
                List.of(ORIGIN, new BlockCoord(11, 70, -5), new BlockCoord(10, 70, -4), new BlockCoord(10, 71, -5));
        Schematic next = schematic(
                new Schematic.Entry(0, 0, 0, "minecraft:stone_bricks"),
                new Schematic.Entry(1, 0, 0, "minecraft:oak_planks"));

        List<BlockCoord> extra = SchematicUpgrade.extraPositions(previous, next, ORIGIN, BuildRotation.NONE);

        assertEquals(
                List.of(new BlockCoord(10, 71, -5), new BlockCoord(10, 70, -4)),
                extra,
                "Старая схема N снимается только там, где новая схема N+1 ничего не ставит: иначе повышение дома "
                        + "либо оставляет лишние блоки, либо ломает уже совпадающие со схемой");
    }

    @Test
    void extraPositions_SameCellDifferentBlock_StaysProtected() {
        Schematic next = schematic(new Schematic.Entry(0, 0, 0, "minecraft:stone_bricks"));

        assertEquals(
                List.of(),
                SchematicUpgrade.extraPositions(List.of(ORIGIN), next, ORIGIN, BuildRotation.NONE),
                "Клетка, которая есть и в новой схеме (пусть с другим блоком), не снимается: её заменяет ремонт, "
                        + "а не снос");
    }

    @Test
    void extraPositions_PreviousScheme_CellsComparedExactly() {
        Schematic previous = schematic(
                new Schematic.Entry(0, 0, 0, "minecraft:oak_planks"),
                new Schematic.Entry(1, 1, 0, "minecraft:oak_planks"));
        Schematic next = schematic(new Schematic.Entry(0, 0, 0, "minecraft:stone_bricks"));

        assertEquals(
                List.of(new BlockCoord(11, 71, -5)),
                SchematicUpgrade.extraPositions(previous, next, ORIGIN, BuildRotation.NONE),
                "Старая схема из задания даёт точные клетки: лишний блок уровня снимается по позиции, "
                        + "а не эвристикой по меткам духа");
    }

    private static Schematic schematic(Schematic.Entry... entries) {
        return new Schematic(2, 2, 2, List.of(entries));
    }
}

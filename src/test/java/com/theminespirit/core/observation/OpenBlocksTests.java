package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenBlocksTests {
    @ParameterizedTest
    @ValueSource(strings = {"minecraft:iron_ore", "minecraft:deepslate_diamond_ore", "minecraft:stone",
            "minecraft:deepslate", "minecraft:oak_log", "minecraft:birch_wood", "minecraft:crimson_stem"})
    void isImportant_OreStoneOrLog_True(String block) {
        assertTrue(OpenBlocks.isImportant(block),
                block + " — руда, камень или бревно: стратег обязан видеть его открытую позицию, чтобы вести духа "
                        + "к ближайшей руде или стволу, а не искать вслепую");
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:oak_planks", "minecraft:glass", "minecraft:air", "minecraft:cobblestone",
            "minecraft:short_grass", "minecraft:chest", "stone_bricks"})
    void isImportant_PlayerMadeOrUnlisted_False(String block) {
        assertFalse(OpenBlocks.isImportant(block),
                block + " не входит в руды, камень и брёвна: открытые позиции строений не должны тратить предел "
                        + "наблюдения и подсказывать стратегу ломать постройки игрока");
    }

    @Test
    void keepNearest_MoreThanLimit_KeepsNearestInDistanceOrder() {
        List<OpenBlock> kept = new ArrayList<>();
        OpenBlocks.keepNearest(kept, block("stone", 9.0), 3);
        OpenBlocks.keepNearest(kept, block("oak_log", 2.0), 3);
        OpenBlocks.keepNearest(kept, block("iron_ore", 7.0), 3);
        OpenBlocks.keepNearest(kept, block("coal_ore", 1.0), 3);
        OpenBlocks.keepNearest(kept, block("deepslate", 5.0), 3);

        assertEquals(List.of(block("coal_ore", 1.0), block("oak_log", 2.0), block("deepslate", 5.0)), kept,
                "Предел наблюдения обязан оставлять ближайшие открытые блоки: ответ стратегу не должен расти "
                        + "с каждой жилой в радиусе, но и терять ближайшую руду из-за дальней нельзя");
    }

    @Test
    void keepNearest_SameDistance_SortsByItemAndPosition() {
        List<OpenBlock> kept = new ArrayList<>();
        OpenBlocks.keepNearest(kept, open("oak_log", new BlockCoord(4, 64, 0), 2.0), 3);
        OpenBlocks.keepNearest(kept, open("stone", new BlockCoord(2, 64, 0), 2.0), 3);
        OpenBlocks.keepNearest(kept, open("iron_ore", new BlockCoord(1, 64, 0), 2.0), 3);

        assertEquals(List.of(open("iron_ore", new BlockCoord(1, 64, 0), 2.0),
                open("oak_log", new BlockCoord(4, 64, 0), 2.0),
                open("stone", new BlockCoord(2, 64, 0), 2.0)), kept,
                "При равном расстоянии порядок задают имя и координаты: иначе один и тот же мир даёт разный "
                        + "ответ и hash наблюдения при каждом скане");
    }

    @Test
    void keepNearest_ZeroLimit_KeepsNothing() {
        List<OpenBlock> kept = new ArrayList<>();
        OpenBlocks.keepNearest(kept, block("stone", 1.0), 0);

        assertEquals(List.of(), kept,
                "Предел 0 выключает сбор открытых блоков: сканы, которым позиции не нужны, не должны тратить "
                        + "работу на их поиск");
    }

    private static OpenBlock block(String item, double distance) {
        return open(item, new BlockCoord((int) distance, 64, 0), distance);
    }

    private static OpenBlock open(String item, BlockCoord pos, double distance) {
        return new OpenBlock(item, pos, distance);
    }
}

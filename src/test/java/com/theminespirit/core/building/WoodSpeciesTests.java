package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WoodSpeciesTests {

    @Test
    void of_JungleLogsOnly_Jungle() {
        assertEquals(
                "jungle",
                WoodSpecies.of(Map.of("jungle_log", 12, "cobblestone", 40)),
                "В джунглях дуба нет — строят из джунглевого дерева, а не ждут дуб (INC-36)");
    }

    @Test
    void of_NoLogsOrOakEqual_Oak() {
        assertEquals(WoodSpecies.OAK, WoodSpecies.of(Map.of()), "Без брёвен — по схеме, дубом");
        assertEquals(WoodSpecies.OAK, WoodSpecies.of(Map.of("oak_log", 5, "birch_log", 5)), "При равенстве — дуб");
    }

    @Test
    void swap_OakBlocks_BecomeSpecies() {
        assertEquals("minecraft:jungle_fence", WoodSpecies.swap("minecraft:oak_fence", "jungle"), "Забор");
        assertEquals("spruce_planks", WoodSpecies.swap("oak_planks", "spruce"), "Доски без пространства имён");
        assertEquals("minecraft:cobblestone", WoodSpecies.swap("minecraft:cobblestone", "jungle"), "Камень как есть");
    }

    @Test
    void swap_Schematic_ReplacesOnlyWood() {
        Schematic schematic = new Schematic(
                1,
                2,
                1,
                List.of(
                        new Schematic.Entry(0, 0, 0, "minecraft:cobblestone"),
                        new Schematic.Entry(0, 1, 0, "minecraft:oak_fence")));

        Schematic swapped = WoodSpecies.swap(schematic, "acacia");

        assertEquals(
                List.of("minecraft:cobblestone", "minecraft:acacia_fence"),
                swapped.blocks().stream().map(Schematic.Entry::block).toList(),
                "Порода меняет только деревянные блоки схемы");
    }

    @Test
    void check_UnknownSpecies_Rejected() {
        assertThrows(IllegalArgumentException.class, () -> WoodSpecies.check("crimson"), "Незнакомая порода");
    }

    @Test
    void prefer_PlanksInStock_TakesHeldVariant() {
        List<String> planks = List.of("minecraft:oak_planks", "minecraft:jungle_planks", "minecraft:bamboo_planks");

        assertEquals(
                Optional.of("minecraft:jungle_planks"),
                WoodSpecies.prefer(planks, Map.of("jungle_planks", 3, "stick", 9), "oak"),
                "Доски в запасе важнее общего порядка");
    }

    @Test
    void prefer_EmptyStockJungle_TakesSpeciesVariant() {
        List<String> planks = List.of("minecraft:bamboo_planks", "minecraft:oak_planks", "minecraft:jungle_planks");

        assertEquals(
                Optional.of("minecraft:jungle_planks"),
                WoodSpecies.prefer(planks, Map.of(), "jungle"),
                "Без запаса — доски породы поселения, а не первые в теге");
        assertEquals(Optional.empty(), WoodSpecies.prefer(planks, Map.of(), "oak"), "Дуб — общий порядок");
    }
}

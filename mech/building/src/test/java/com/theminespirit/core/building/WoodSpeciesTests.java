package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.api.WoodSpecies;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WoodSpeciesTests {

    @Test
    void sameRole_LogOfOtherSpecies_KeptByBuilder() {
        assertTrue(
                WoodSpecies.sameRole("minecraft:jungle_log", "oak_log"),
                "Столб jungle_log на месте oak_log схемы — тот же блок поселения: стройка его не сносит (INC-58)"
                        + " Иначе тест sameRole_LogOfOtherSpecies_KeptByBuilder перестанет ловить эту поломку. При смене sameRole_LogOfOtherSpecies_KeptByBuilder проверь это утверждение.");
        assertTrue(
                WoodSpecies.sameRole("dark_oak_fence", "minecraft:oak_fence"),
                "Забор любой породы — забор"
                        + " Иначе тест sameRole_LogOfOtherSpecies_KeptByBuilder перестанет ловить эту поломку. При смене sameRole_LogOfOtherSpecies_KeptByBuilder проверь это утверждение.");
        assertFalse(
                WoodSpecies.sameRole("jungle_log", "oak_planks"),
                "Бревно на месте досок — другой блок: стройка его заменяет"
                        + " Иначе тест sameRole_LogOfOtherSpecies_KeptByBuilder перестанет ловить эту поломку. При смене sameRole_LogOfOtherSpecies_KeptByBuilder проверь это утверждение.");
        assertFalse(
                WoodSpecies.sameRole("stone", "oak_log"),
                "Не деревянный блок — не та же роль"
                        + " Иначе тест sameRole_LogOfOtherSpecies_KeptByBuilder перестанет ловить эту поломку. При смене sameRole_LogOfOtherSpecies_KeptByBuilder проверь это утверждение.");
    }

    @Test
    void of_JungleLogsOnly_Jungle() {
        assertEquals(
                "jungle",
                WoodSpecies.of(Map.of("jungle_log", 12, "cobblestone", 40)),
                "В джунглях дуба нет — строят из джунглевого дерева, а не ждут дуб (INC-36)"
                        + " Иначе тест of_JungleLogsOnly_Jungle перестанет ловить эту поломку. При смене of_JungleLogsOnly_Jungle проверь это утверждение.");
    }

    @Test
    void ofBiome_JungleBiomes_Jungle() {
        for (String biome : List.of("minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle")) {
            assertEquals(
                    "jungle",
                    WoodSpecies.ofBiome(biome),
                    "Без брёвен рядом ограда в джунглях — из jungle, а не из дуба (INC-37): " + biome
                            + " Иначе тест ofBiome_JungleBiomes_Jungle перестанет ловить эту поломку. При смене ofBiome_JungleBiomes_Jungle проверь это утверждение.");
        }
    }

    @Test
    void ofBiome_OtherBiomes_TheirWood() {
        assertEquals(
                "spruce",
                WoodSpecies.ofBiome("minecraft:snowy_taiga"),
                "тайга — ель"
                        + " Иначе тест ofBiome_OtherBiomes_TheirWood перестанет ловить эту поломку. При смене ofBiome_OtherBiomes_TheirWood проверь это утверждение.");
        assertEquals(
                "cherry",
                WoodSpecies.ofBiome("minecraft:cherry_grove"),
                "вишнёвая роща — вишня, не ель"
                        + " Иначе тест ofBiome_OtherBiomes_TheirWood перестанет ловить эту поломку. При смене ofBiome_OtherBiomes_TheirWood проверь это утверждение.");
        assertEquals(
                "dark_oak",
                WoodSpecies.ofBiome("minecraft:dark_forest"),
                "тёмный лес — тёмный дуб"
                        + " Иначе тест ofBiome_OtherBiomes_TheirWood перестанет ловить эту поломку. При смене ofBiome_OtherBiomes_TheirWood проверь это утверждение.");
        assertEquals(
                "acacia",
                WoodSpecies.ofBiome("minecraft:savanna_plateau"),
                "саванна — акация"
                        + " Иначе тест ofBiome_OtherBiomes_TheirWood перестанет ловить эту поломку. При смене ofBiome_OtherBiomes_TheirWood проверь это утверждение.");
        assertEquals(
                WoodSpecies.OAK,
                WoodSpecies.ofBiome("minecraft:plains"),
                "равнины — дуб"
                        + " Иначе тест ofBiome_OtherBiomes_TheirWood перестанет ловить эту поломку. При смене ofBiome_OtherBiomes_TheirWood проверь это утверждение.");
    }

    @Test
    void of_NoLogsOrOakEqual_Oak() {
        assertEquals(
                WoodSpecies.OAK,
                WoodSpecies.of(Map.of()),
                "Без брёвен — по схеме, дубом"
                        + " Иначе тест of_NoLogsOrOakEqual_Oak перестанет ловить эту поломку. При смене of_NoLogsOrOakEqual_Oak проверь это утверждение.");
        assertEquals(
                WoodSpecies.OAK,
                WoodSpecies.of(Map.of("oak_log", 5, "birch_log", 5)),
                "При равенстве — дуб"
                        + " Иначе тест of_NoLogsOrOakEqual_Oak перестанет ловить эту поломку. При смене of_NoLogsOrOakEqual_Oak проверь это утверждение.");
    }

    @Test
    void swap_OakBlocks_BecomeSpecies() {
        assertEquals(
                "minecraft:jungle_fence",
                WoodSpecies.swap("minecraft:oak_fence", "jungle"),
                "Забор"
                        + " Иначе тест swap_OakBlocks_BecomeSpecies перестанет ловить эту поломку. При смене swap_OakBlocks_BecomeSpecies проверь это утверждение.");
        assertEquals(
                "spruce_planks",
                WoodSpecies.swap("oak_planks", "spruce"),
                "Доски без пространства имён"
                        + " Иначе тест swap_OakBlocks_BecomeSpecies перестанет ловить эту поломку. При смене swap_OakBlocks_BecomeSpecies проверь это утверждение.");
        assertEquals(
                "minecraft:cobblestone",
                WoodSpecies.swap("minecraft:cobblestone", "jungle"),
                "Камень как есть"
                        + " Иначе тест swap_OakBlocks_BecomeSpecies перестанет ловить эту поломку. При смене swap_OakBlocks_BecomeSpecies проверь это утверждение.");
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

        Schematic swapped = SchematicWood.swap(schematic, "acacia");

        assertEquals(
                List.of("minecraft:cobblestone", "minecraft:acacia_fence"),
                swapped.blocks().stream().map(Schematic.Entry::block).toList(),
                "Порода меняет только деревянные блоки схемы"
                        + " Иначе тест swap_Schematic_ReplacesOnlyWood перестанет ловить эту поломку. При смене swap_Schematic_ReplacesOnlyWood проверь это утверждение.");
    }

    @Test
    void check_UnknownSpecies_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WoodSpecies.check("crimson"),
                "Незнакомая порода"
                        + " Иначе тест check_UnknownSpecies_Rejected перестанет ловить эту поломку. При смене check_UnknownSpecies_Rejected проверь это утверждение.");
    }

    @Test
    void prefer_PlanksInStock_TakesHeldVariant() {
        List<String> planks = List.of("minecraft:oak_planks", "minecraft:jungle_planks", "minecraft:bamboo_planks");

        assertEquals(
                Optional.of("minecraft:jungle_planks"),
                WoodSpecies.prefer(planks, Map.of("jungle_planks", 3, "stick", 9), "oak"),
                "Доски в запасе важнее общего порядка"
                        + " Иначе тест prefer_PlanksInStock_TakesHeldVariant перестанет ловить эту поломку. При смене prefer_PlanksInStock_TakesHeldVariant проверь это утверждение.");
    }

    @Test
    void prefer_EmptyStockJungle_TakesSpeciesVariant() {
        List<String> planks = List.of("minecraft:bamboo_planks", "minecraft:oak_planks", "minecraft:jungle_planks");

        assertEquals(
                Optional.of("minecraft:jungle_planks"),
                WoodSpecies.prefer(planks, Map.of(), "jungle"),
                "Без запаса — доски породы поселения, а не первые в теге"
                        + " Иначе тест prefer_EmptyStockJungle_TakesSpeciesVariant перестанет ловить эту поломку. При смене prefer_EmptyStockJungle_TakesSpeciesVariant проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                WoodSpecies.prefer(planks, Map.of(), "oak"),
                "Дуб — общий порядок"
                        + " Иначе тест prefer_EmptyStockJungle_TakesSpeciesVariant перестанет ловить эту поломку. При смене prefer_EmptyStockJungle_TakesSpeciesVariant проверь это утверждение.");
    }

    @Test
    void speciesOf_WoodenItem_ReturnsPrefixSpecies() {
        assertEquals(
                Optional.of("jungle"),
                WoodSpecies.speciesOf("minecraft:jungle_fence"),
                "забор джунглей"
                        + " Иначе тест speciesOf_WoodenItem_ReturnsPrefixSpecies перестанет ловить эту поломку. При смене speciesOf_WoodenItem_ReturnsPrefixSpecies проверь это утверждение.");
        assertEquals(
                Optional.of("oak"),
                WoodSpecies.speciesOf("oak_fence_gate:2"),
                "id второй доли добычи"
                        + " Иначе тест speciesOf_WoodenItem_ReturnsPrefixSpecies перестанет ловить эту поломку. При смене speciesOf_WoodenItem_ReturnsPrefixSpecies проверь это утверждение.");
        assertEquals(
                Optional.of("dark_oak"),
                WoodSpecies.speciesOf("dark_oak_log"),
                "тёмный дуб — не дуб"
                        + " Иначе тест speciesOf_WoodenItem_ReturnsPrefixSpecies перестанет ловить эту поломку. При смене speciesOf_WoodenItem_ReturnsPrefixSpecies проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                WoodSpecies.speciesOf("cobblestone"),
                "не дерево"
                        + " Иначе тест speciesOf_WoodenItem_ReturnsPrefixSpecies перестанет ловить эту поломку. При смене speciesOf_WoodenItem_ReturnsPrefixSpecies проверь это утверждение.");
    }
}

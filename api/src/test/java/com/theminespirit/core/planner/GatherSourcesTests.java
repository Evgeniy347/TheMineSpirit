package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.farming.api.CropKind;
import com.theminespirit.core.forestry.api.TreeSpecies;
import com.theminespirit.core.planner.api.GatherSources;
import org.junit.jupiter.api.Test;

class GatherSourcesTests {
    @Test
    void sourceBlock_Sapling_IsLeaves() {
        assertEquals(
                "minecraft:jungle_leaves",
                GatherSources.sourceBlock("jungle_sapling").orElseThrow(),
                "Саженец берётся с листвы своей породы, а не выкапыванием посаженного: живой отказ "
                        + "«jungle_sapling появляется крафтом» (10.11)"
                        + " Иначе тест sourceBlock_Sapling_IsLeaves перестанет ловить эту поломку. При смене sourceBlock_Sapling_IsLeaves проверь это утверждение.");
        assertEquals(
                "minecraft:oak_leaves",
                GatherSources.sourceBlock("minecraft:oak_sapling").orElseThrow(),
                "Пространство имён не меняет источник саженца"
                        + " Иначе тест sourceBlock_Sapling_IsLeaves перестанет ловить эту поломку. При смене sourceBlock_Sapling_IsLeaves проверь это утверждение.");
    }

    @Test
    void sourceBlock_Berries_IsBush() {
        assertEquals(
                "minecraft:sweet_berry_bush",
                GatherSources.sourceBlock("sweet_berries").orElseThrow(),
                "Ягоды снимаются с куста: живой отказ «sweet_berries не найден способ добычи» (10.11)"
                        + " Иначе тест sourceBlock_Berries_IsBush перестанет ловить эту поломку. При смене sourceBlock_Berries_IsBush проверь это утверждение.");
    }

    @Test
    void sourceBlock_FruitCrop_IsFruitBlockNotStem() {
        assertEquals(
                "minecraft:melon",
                GatherSources.sourceBlock("melon_slice").orElseThrow(),
                "Ломтик даёт плод мелона, а не стебель: стебель не ломают ради урожая"
                        + " Иначе тест sourceBlock_FruitCrop_IsFruitBlockNotStem перестанет ловить эту поломку. При смене sourceBlock_FruitCrop_IsFruitBlockNotStem проверь это утверждение.");
        assertEquals(
                "minecraft:pumpkin",
                GatherSources.sourceBlock("pumpkin").orElseThrow(),
                "Тыква — плод на стебле"
                        + " Иначе тест sourceBlock_FruitCrop_IsFruitBlockNotStem перестанет ловить эту поломку. При смене sourceBlock_FruitCrop_IsFruitBlockNotStem проверь это утверждение.");
    }

    @Test
    void sourceBlock_NaturalBlock_HasNoSeparateSource() {
        assertTrue(
                GatherSources.sourceBlock("oak_log").isEmpty(),
                "Бревно добывается своим блоком, отдельный источник не выдумываем"
                        + " Иначе тест sourceBlock_NaturalBlock_HasNoSeparateSource перестанет ловить эту поломку. При смене sourceBlock_NaturalBlock_HasNoSeparateSource проверь это утверждение.");
        assertTrue(
                GatherSources.sourceBlock("dirt").isEmpty(),
                "Земля — сама себе источник"
                        + " Иначе тест sourceBlock_NaturalBlock_HasNoSeparateSource перестанет ловить эту поломку. При смене sourceBlock_NaturalBlock_HasNoSeparateSource проверь это утверждение.");
    }

    @Test
    void ofSaplingAndProduce_KnownNames_FindSpeciesAndCrop() {
        assertEquals(
                "jungle",
                TreeSpecies.ofSapling("minecraft:jungle_sapling").orElseThrow().id(),
                "Порода саженца находится по имени"
                        + " Иначе тест ofSaplingAndProduce_KnownNames_FindSpeciesAndCrop перестанет ловить эту поломку. При смене ofSaplingAndProduce_KnownNames_FindSpeciesAndCrop проверь это утверждение.");
        assertEquals(
                CropKind.SWEET_BERRY_BUSH,
                CropKind.ofProduce("sweet_berries").orElseThrow(),
                "Культура находится по урожаю"
                        + " Иначе тест ofSaplingAndProduce_KnownNames_FindSpeciesAndCrop перестанет ловить эту поломку. При смене ofSaplingAndProduce_KnownNames_FindSpeciesAndCrop проверь это утверждение.");
        assertTrue(
                CropKind.ofProduce("oak_log").isEmpty(),
                "Бревно — не урожай"
                        + " Иначе тест ofSaplingAndProduce_KnownNames_FindSpeciesAndCrop перестанет ловить эту поломку. При смене ofSaplingAndProduce_KnownNames_FindSpeciesAndCrop проверь это утверждение.");
    }
}

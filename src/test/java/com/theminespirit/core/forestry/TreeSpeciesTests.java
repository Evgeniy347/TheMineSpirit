package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TreeSpeciesTests {
    @Test
    void all_EightSpecies_ListLogsLeavesAndSaplings() {
        assertEquals(
                List.of(
                        new TreeSpecies("oak", "minecraft:oak_log", "minecraft:oak_leaves", "minecraft:oak_sapling"),
                        new TreeSpecies("spruce", "minecraft:spruce_log", "minecraft:spruce_leaves",
                                "minecraft:spruce_sapling"),
                        new TreeSpecies("birch", "minecraft:birch_log", "minecraft:birch_leaves",
                                "minecraft:birch_sapling"),
                        new TreeSpecies("jungle", "minecraft:jungle_log", "minecraft:jungle_leaves",
                                "minecraft:jungle_sapling"),
                        new TreeSpecies("acacia", "minecraft:acacia_log", "minecraft:acacia_leaves",
                                "minecraft:acacia_sapling"),
                        new TreeSpecies("dark_oak", "minecraft:dark_oak_log", "minecraft:dark_oak_leaves",
                                "minecraft:dark_oak_sapling"),
                        new TreeSpecies("mangrove", "minecraft:mangrove_log", "minecraft:mangrove_leaves",
                                "minecraft:mangrove_propagule"),
                        new TreeSpecies("cherry", "minecraft:cherry_log", "minecraft:cherry_leaves",
                                "minecraft:cherry_sapling")),
                TreeSpecies.ALL,
                "Таблица пород обязана называть ровно восемь видов с их брёвнами, листвой и саженцем: "
                        + "по id-шникам поиск находит деревья в мире, а пропущенная порода тихо даёт «деревьев нет»");
    }

    @Test
    void parse_Any_ReturnsAllSpecies() {
        assertEquals(TreeSpecies.ALL, TreeSpecies.parse("any"),
                "parse('any') обязан вернуть все породы: запись any в плане значит «рубить что найду», "
                        + "а не «порубу ничего»");
    }

    @Test
    void parse_KnownId_ReturnsThatSpecies() {
        assertEquals(List.of(new TreeSpecies("oak", "minecraft:oak_log", "minecraft:oak_leaves",
                        "minecraft:oak_sapling")),
                TreeSpecies.parse("oak"),
                "parse известной породы обязан вернуть ровно её: по названию из плана дух ищет брёвна конкретного вида");
    }

    @Test
    void parse_UnknownId_ThrowsListingAllowed() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> TreeSpecies.parse("pine"),
                "parse неизвестной породы обязан отказывать: опечатка в названии иначе тихо рубит любое дерево");

        assertTrue(error.getMessage().contains("oak") && error.getMessage().contains("any"),
                "Сообщение обязано перечислить допустимые породы вместе с 'any': по нему игрок правит название. "
                        + "Фактическое: " + error.getMessage());
    }

    @Test
    void parse_Null_IsRejected() {
        assertThrows(NullPointerException.class, () -> TreeSpecies.parse(null),
                "parse(null) обязан отказывать: без имени породы нельзя выбрать вид для рубки");
    }

    @Test
    void ofLog_OakLog_ReturnsOak() {
        assertEquals(Optional.of(new TreeSpecies("oak", "minecraft:oak_log", "minecraft:oak_leaves",
                        "minecraft:oak_sapling")),
                TreeSpecies.ofLog("minecraft:oak_log"),
                "ofLog по настоящему бревну обязан вернуть его породу: по нему дух понимает, что перед ним дерево");
    }

    @Test
    void ofLog_StrippedLog_IsEmpty() {
        assertEquals(Optional.empty(), TreeSpecies.ofLog("minecraft:stripped_oak_log"),
                "ofLog тёсаного бревна обязан вернуть пусто: stripped_* — строительный материал, а не дерево, "
                        + "его дух ломать не должен");
    }

    @Test
    void ofLog_WoodBlock_IsEmpty() {
        assertEquals(Optional.empty(), TreeSpecies.ofLog("minecraft:oak_wood"),
                "ofLog блока *_wood обязан вернуть пусто: wood — брёвна без коры, из них делают постройки, "
                        + "а не рубят деревья");
    }

    @Test
    void names_AnyFirst_ThenEightSpecies() {
        assertEquals(List.of("any", "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"),
                TreeSpecies.names(),
                "Список имён для enum обязан начинаться с 'any' и перечислить все породы: по нему валидатор плана "
                        + "принимает только осмысленные значения species");
    }
}
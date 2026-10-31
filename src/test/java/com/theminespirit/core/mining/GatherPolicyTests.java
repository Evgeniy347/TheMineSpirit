package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class GatherPolicyTests {
    private static final Set<String> FORBIDDEN_EXACT = Set.of(
            "bedrock",
            "barrier",
            "command_block",
            "chain_command_block",
            "repeating_command_block",
            "structure_block",
            "jigsaw",
            "spawner",
            "trial_spawner",
            "vault",
            "end_portal",
            "end_portal_frame",
            "nether_portal",
            "hopper",
            "dispenser",
            "dropper",
            "furnace",
            "blast_furnace",
            "smoker",
            "brewing_stand",
            "enchanting_table",
            "chest",
            "trapped_chest",
            "ender_chest",
            "barrel",
            "shulker_box");

    static Stream<String> forbiddenExactIds() {
        return FORBIDDEN_EXACT.stream();
    }

    @Test
    void check_ForbiddenTable_MatchesProduction() {
        assertEquals(
                FORBIDDEN_EXACT,
                GatherPolicy.forbiddenExactIds(),
                "Таблица запретов теста и кода должна совпадать: иначе запрет незаметно разойдётся с документацией");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("forbiddenExactIds")
    void check_ForbiddenExactId_IsRefused(String blockId) {
        GatherPolicy.Decision decision = GatherPolicy.check(blockId);

        assertTrue(
                decision instanceof GatherPolicy.Refused refused
                        && refused.reason().contains(blockId),
                "Точный запрещённый id '" + blockId + "' обязан отвергаться с причиной: добыча контейнеров, "
                        + "порталов и неразрушимых блоков теряет чужой лут или ломает мир");
    }

    @Test
    void check_NamespacedForbiddenId_IsRefused() {
        assertTrue(
                GatherPolicy.check("minecraft:chest") instanceof GatherPolicy.Refused,
                "Проверка идёт по пути id после ':', поэтому 'minecraft:chest' обязан отвергаться как 'chest'");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"red_shulker_box", "white_bed", "oak_chest", "mod:blue_chest", "mod:my_shulker_box"})
    void check_ForbiddenFamilyId_IsRefused(String blockId) {
        GatherPolicy.Decision decision = GatherPolicy.check(blockId);

        assertTrue(
                decision instanceof GatherPolicy.Refused refused
                        && refused.reason().contains(blockId),
                "Семейство '" + blockId + "' (суффикс *_chest/*_shulker_box/*_bed) обязано отвергаться: "
                        + "шаблон запроса может накрыть запрещённый id, поэтому проверяется каждый найденный блок");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "stone",
                "cobblestone",
                "sand",
                "gravel",
                "dirt",
                "oak_log",
                "iron_ore",
                "deepslate",
                "nether_quartz_ore",
                "ancient_debris",
                "minecraft:stone"
            })
    void check_NaturalBlock_IsAllowed(String blockId) {
        assertTrue(
                GatherPolicy.check(blockId) instanceof GatherPolicy.Allowed,
                "Природный блок '" + blockId + "' обязан разрешаться: иначе gather не соберёт обычные ресурсы");
    }

    @Test
    void check_NullId_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> GatherPolicy.check(null),
                "GatherPolicy.check(null) обязан отказывать: без id непонятно, что запрещать");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"oak_planks", "minecraft:oak_planks", "oak_fence", "oak_stairs", "glass", "chest"})
    void sourceCheck_CraftedFamily_IsRefused(String blockId) {
        assertTrue(
                GatherPolicy.sourceCheck(blockId) instanceof GatherPolicy.Refused,
                "Крафтовый '" + blockId + "' нельзя брать из мира как ресурс: размещённые доски и заборы — "
                        + "постройки, а не запас духа (observe9: supply_gather:oak_planks искал доски блоком)");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"oak_log", "minecraft:oak_log", "stone", "dirt", "oak_leaves"})
    void sourceCheck_NaturalBlock_IsAllowed(String blockId) {
        assertTrue(
                GatherPolicy.sourceCheck(blockId) instanceof GatherPolicy.Allowed,
                "Природный блок '" + blockId + "' обязан оставаться источником добычи");
    }
}

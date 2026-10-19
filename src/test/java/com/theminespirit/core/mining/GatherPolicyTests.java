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
            "bedrock", "barrier",
            "command_block", "chain_command_block", "repeating_command_block",
            "structure_block", "jigsaw",
            "spawner", "trial_spawner", "vault",
            "end_portal", "end_portal_frame", "nether_portal",
            "hopper", "dispenser", "dropper",
            "furnace", "blast_furnace", "smoker",
            "brewing_stand", "enchanting_table",
            "chest", "trapped_chest", "ender_chest", "barrel", "shulker_box");

    static Stream<String> forbiddenExactIds() {
        return FORBIDDEN_EXACT.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("forbiddenExactIds")
    void check_ForbiddenExactId_IsRefused(String blockId) {
        GatherPolicy.Decision decision = GatherPolicy.check(blockId);

        assertTrue(decision instanceof GatherPolicy.Refused refused
                        && refused.reason().contains(blockId),
                "Точный запрещённый id '" + blockId + "' обязан отвергаться с причиной: добыча контейнеров, "
                        + "порталов и неразрушимых блоков теряет чужой лут или ломает мир");
    }

    @Test
    void check_NamespacedForbiddenId_IsRefused() {
        assertTrue(GatherPolicy.check("minecraft:chest") instanceof GatherPolicy.Refused,
                "Проверка идёт по пути id после ':', поэтому 'minecraft:chest' обязан отвергаться как 'chest'");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"red_shulker_box", "white_bed", "oak_chest", "mod:blue_chest", "mod:my_shulker_box"})
    void check_ForbiddenFamilyId_IsRefused(String blockId) {
        GatherPolicy.Decision decision = GatherPolicy.check(blockId);

        assertTrue(decision instanceof GatherPolicy.Refused refused
                        && refused.reason().contains(blockId),
                "Семейство '" + blockId + "' (суффикс *_chest/*_shulker_box/*_bed) обязано отвергаться: "
                        + "шаблон запроса может накрыть запрещённый id, поэтому проверяется каждый найденный блок");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"stone", "cobblestone", "sand", "gravel", "dirt", "oak_log", "iron_ore",
            "deepslate", "nether_quartz_ore", "ancient_debris", "minecraft:stone"})
    void check_NaturalBlock_IsAllowed(String blockId) {
        assertTrue(GatherPolicy.check(blockId) instanceof GatherPolicy.Allowed,
                "Природный блок '" + blockId + "' обязан разрешаться: иначе gather не соберёт обычные ресурсы");
    }

    @Test
    void check_NullId_IsRejected() {
        assertThrows(NullPointerException.class, () -> GatherPolicy.check(null),
                "GatherPolicy.check(null) обязан отказывать: без id непонятно, что запрещать");
    }
}

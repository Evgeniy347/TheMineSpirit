package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.GatherPolicy;
import com.theminespirit.core.policy.api.PolicyId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockProtectionTests {
    private BlockProtection protection;

    @BeforeAll
    void loadProtection() {
        protection = ProtectionConfig.readClasspath();
    }

    static List<String> around(int artificial, String artificialId, String filler) {
        List<String> list = new ArrayList<>(Collections.nCopies(artificial, artificialId));
        list.addAll(Collections.nCopies(26 - artificial, filler));
        return list;
    }

    static List<String> forest() {
        return around(0, "", "minecraft:oak_leaves");
    }

    static List<String> house() {
        List<String> list = new ArrayList<>(Collections.nCopies(10, "minecraft:oak_planks"));
        list.addAll(Collections.nCopies(4, "minecraft:glass"));
        list.addAll(Collections.nCopies(12, "minecraft:air"));
        return list;
    }

    private static Arguments row(String name, String block, List<String> neighbours, boolean zone, PolicyId refusedBy) {
        return Arguments.of(name, block, neighbours, zone, refusedBy);
    }

    static Stream<Arguments> table() {
        List<String> cave = around(0, "", "minecraft:stone");
        return Stream.of(
                row("бревно в лесу", "minecraft:oak_log", forest(), false, null),
                row("бревно в доме из досок и стекла", "minecraft:oak_log", house(), false, PolicyId.PLAYER_BUILDINGS),
                row("камень в защищённой зоне", "minecraft:stone", cave, true, PolicyId.PROTECTED_ZONE),
                row("булыжник в защищённой зоне", "minecraft:cobblestone", cave, true, PolicyId.PROTECTED_ZONE),
                row("сундук", "minecraft:chest", cave, false, PolicyId.PROTECTED_BLOCKS),
                row("рамка портала края", "minecraft:end_portal_frame", cave, false, PolicyId.PROTECTED_BLOCKS),
                row("маяк", "minecraft:beacon", cave, false, PolicyId.PROTECTED_BLOCKS),
                row(
                        "укреплённый глубинный сланец",
                        "minecraft:reinforced_deepslate",
                        cave,
                        false,
                        PolicyId.PROTECTED_BLOCKS),
                row("магнетит", "minecraft:lodestone", cave, false, PolicyId.PROTECTED_BLOCKS),
                row("якорь возрождения", "minecraft:respawn_anchor", cave, false, PolicyId.PROTECTED_BLOCKS),
                row("алмазная руда в пещере", "minecraft:diamond_ore", cave, false, null),
                row("красная руда — не механизм", "minecraft:redstone_ore", cave, false, null),
                row("глубинная железная руда", "minecraft:deepslate_iron_ore", cave, false, null),
                row("песок в пустыне", "minecraft:sand", around(0, "", "minecraft:sand"), false, null),
                row(
                        "камень у чужой стены из 5 блоков",
                        "minecraft:stone",
                        around(5, "minecraft:bricks", "minecraft:stone"),
                        false,
                        null),
                row(
                        "камень у чужой стены из 6 блоков",
                        "minecraft:stone",
                        around(6, "minecraft:bricks", "minecraft:stone"),
                        false,
                        PolicyId.PLAYER_BUILDINGS),
                row(
                        "камень у стены из 26 блоков",
                        "minecraft:stone",
                        around(26, "minecraft:bricks", "minecraft:stone"),
                        false,
                        PolicyId.PLAYER_BUILDINGS),
                row(
                        "неизвестный блок мода считается искусственным: 6 штук",
                        "minecraft:stone",
                        around(6, "othermod:weird_block", "minecraft:stone"),
                        false,
                        PolicyId.PLAYER_BUILDINGS),
                row(
                        "неизвестный блок мода: 5 штук",
                        "minecraft:stone",
                        around(5, "othermod:weird_block", "minecraft:stone"),
                        false,
                        null),
                row(
                        "технические невидимые блоки не считаются постройкой",
                        "minecraft:oak_log",
                        around(26, "minecraft:barrier", "minecraft:barrier"),
                        false,
                        null),
                row(
                        "воздух вокруг не считается постройкой",
                        "minecraft:stone",
                        around(0, "", "minecraft:air"),
                        false,
                        null),
                row(
                        "сундук в защищённой зоне: чёрный список раньше зоны",
                        "minecraft:chest",
                        cave,
                        true,
                        PolicyId.PROTECTED_BLOCKS),
                row(
                        "камень в зоне, поставленный игроком: зона раньше игрока",
                        "minecraft:stone",
                        cave,
                        true,
                        PolicyId.PROTECTED_ZONE),
                row(
                        "бревно игрока в доме: игрок раньше здания",
                        "minecraft:oak_log",
                        house(),
                        false,
                        PolicyId.PLAYER_BUILDINGS));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    void check_Row_RefusesByTheFirstMatchingRule(
            String name, String block, List<String> neighbours, boolean zone, PolicyId refusedBy) {
        BlockProtection.Decision decision =
                protection.check(block, neighbours, new BlockProtection.Placement(zone, false));

        if (refusedBy == null) {
            assertEquals(
                    new BlockProtection.Allowed(),
                    decision,
                    "Строка «" + name + "»: блок природный, ломать можно, иначе добыча встанет на природе. "
                            + "При смене правил проверь строки с refusedBy = null");
        } else {
            BlockProtection.Refused refused = assertInstanceOf(
                    BlockProtection.Refused.class,
                    decision,
                    "Строка «" + name + "»: ломать нельзя, иначе уборка сносит постройки и механизмы. "
                            + "При смене таблицы проверь каждую строку с ожидаемым отказом");
            assertEquals(
                    refusedBy,
                    refused.policy(),
                    "Строка «" + name + "»: порядок правил — чёрный список, зона, игрок, здание, иначе отказ "
                            + "называет не ту причину. Причина: " + refused.reason()
                            + ". При смене порядка проверь PolicyId");
        }
    }

    @Test
    void check_ChestInPlainCave_ReasonComesFromGatherPolicy() {
        BlockProtection.Refused refused = (BlockProtection.Refused)
                protection.check("minecraft:chest", forest(), new BlockProtection.Placement(false, false));

        assertEquals(
                ((GatherPolicy.Refused) GatherPolicy.check("minecraft:chest")).reason(),
                refused.reason(),
                "Контейнеры и механизмы запрещает GatherPolicy, protection.json их не дублирует: причина берётся "
                        + "оттуда, иначе две копии правила разъедутся. При смене check проверь сундук в пещере");
    }

    @Test
    void check_BuildingRefusal_NamesCountAndThreshold() {
        BlockProtection.Refused refused = (BlockProtection.Refused)
                protection.check("minecraft:oak_log", house(), new BlockProtection.Placement(false, false));

        assertEquals(
                "вокруг блока 'minecraft:oak_log' 14 искусственных блоков из 26 (порог 6, например 'minecraft:oak_planks'): это постройка игрока",
                refused.reason(),
                "Отказ называет число искусственных соседей и порог: по итогу задачи видно, почему бревно "
                        + "пропущено, иначе игрок считает защиту случайной. При смене текста проверь оба числа");
    }

    @Test
    void check_WrongNeighbourCount_ThrowsWithCount() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> protection.check(
                        "minecraft:stone", List.of("minecraft:air"), new BlockProtection.Placement(false, false)),
                "25 или 27 соседей — ошибка вызова, иначе защита считает постройку по неполному кубу 3×3×3 "
                        + "и слепнет. При смене NEIGHBOUR_COUNT проверь отказ на одном соседе");

        assertTrue(
                error.getMessage().startsWith("Соседей 1, ожидается 26"),
                "Ошибка называет число и ожидание, иначе вызывающий не поймёт, какой список передать. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь оба числа");
    }

    @Test
    void checkSettlement_OldFenceOnPlot_AllowedChestKept() {
        assertEquals(
                new BlockProtection.Allowed(),
                protection.checkSettlement("minecraft:jungle_fence", false),
                "Старая ограда на участке поселения убирается выравниванием: чёрный список заборов её держал, и ящик "
                        + "проваливался «защищено: 22» (INC-71), иначе выравнивание снова встанет. При смене checkSettlement проверь старый забор");
        assertInstanceOf(
                BlockProtection.Refused.class,
                protection.checkSettlement("minecraft:chest", false),
                "Сундук на участке по-прежнему не ломают: в нём запас поселения, иначе дух разорит склад. "
                        + "При смене checkSettlement проверь отказ на сундуке");
    }

    @Test
    void file_Loaded_ThresholdIsSixAndGatherPolicyIsNotDuplicated() {
        assertEquals(
                6,
                protection.buildingThreshold(),
                "Порог «здания» 6 искусственных соседей из 26 (предварительное значение чеклиста), иначе "
                        + "граница постройки смещается и стены считаются природой. При смене файла проверь порог");
        for (String exact : GatherPolicy.forbiddenExactIds()) {
            assertFalse(
                    protection.denyPatterns().contains("minecraft:" + exact),
                    "protection.json → deny не повторяет точный id '" + exact
                            + "' из GatherPolicy: дубль разъедется при правке одного из списков, и защита "
                            + "начнёт противоречить сама себе и сломается. При смене списков проверь отсутствие пересечения");
        }
    }
}

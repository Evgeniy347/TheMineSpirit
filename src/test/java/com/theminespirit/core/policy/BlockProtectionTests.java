package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.GatherPolicy;
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
import org.junit.jupiter.params.provider.ValueSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockProtectionTests {
    private BlockProtection protection;

    @BeforeAll
    void loadProtection() {
        protection = ProtectionConfig.load();
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

    private static Arguments row(String name, String block, List<String> neighbours, boolean placed, boolean zone, PolicyId refusedBy) {
        return Arguments.of(name, block, neighbours, placed, zone, refusedBy);
    }

    static Stream<Arguments> table() {
        List<String> cave = around(0, "", "minecraft:stone");
        return Stream.of(
                row("бревно в лесу", "minecraft:oak_log", forest(), false, false, null),
                row("бревно в доме из досок и стекла", "minecraft:oak_log", house(), false, false, PolicyId.PLAYER_BUILDINGS),
                row("бревно, поставленное игроком, в лесу", "minecraft:oak_log", forest(), true, false, PolicyId.PLAYER_BUILDINGS),
                row("камень в защищённой зоне", "minecraft:stone", cave, false, true, PolicyId.PROTECTED_ZONE),
                row("булыжник в защищённой зоне", "minecraft:cobblestone", cave, false, true, PolicyId.PROTECTED_ZONE),
                row("сундук", "minecraft:chest", cave, false, false, PolicyId.PROTECTED_BLOCKS),
                row("рамка портала края", "minecraft:end_portal_frame", cave, false, false, PolicyId.PROTECTED_BLOCKS),
                row("маяк", "minecraft:beacon", cave, false, false, PolicyId.PROTECTED_BLOCKS),
                row("укреплённый глубинный сланец", "minecraft:reinforced_deepslate", cave, false, false, PolicyId.PROTECTED_BLOCKS),
                row("магнетит", "minecraft:lodestone", cave, false, false, PolicyId.PROTECTED_BLOCKS),
                row("якорь возрождения", "minecraft:respawn_anchor", cave, false, false, PolicyId.PROTECTED_BLOCKS),
                row("алмазная руда в пещере", "minecraft:diamond_ore", cave, false, false, null),
                row("красная руда — не механизм", "minecraft:redstone_ore", cave, false, false, null),
                row("глубинная железная руда", "minecraft:deepslate_iron_ore", cave, false, false, null),
                row("песок в пустыне", "minecraft:sand", around(0, "", "minecraft:sand"), false, false, null),
                row("камень у чужой стены из 5 блоков", "minecraft:stone", around(5, "minecraft:bricks", "minecraft:stone"), false, false, null),
                row("камень у чужой стены из 6 блоков", "minecraft:stone", around(6, "minecraft:bricks", "minecraft:stone"), false, false, PolicyId.PLAYER_BUILDINGS),
                row("камень у стены из 26 блоков", "minecraft:stone", around(26, "minecraft:bricks", "minecraft:stone"), false, false, PolicyId.PLAYER_BUILDINGS),
                row("неизвестный блок мода считается искусственным: 6 штук", "minecraft:stone", around(6, "othermod:weird_block", "minecraft:stone"), false, false, PolicyId.PLAYER_BUILDINGS),
                row("неизвестный блок мода: 5 штук", "minecraft:stone", around(5, "othermod:weird_block", "minecraft:stone"), false, false, null),
                row("технические невидимые блоки не считаются постройкой", "minecraft:oak_log", around(26, "minecraft:barrier", "minecraft:barrier"), false, false, null),
                row("воздух вокруг не считается постройкой", "minecraft:stone", around(0, "", "minecraft:air"), false, false, null),
                row("сундук в защищённой зоне: чёрный список раньше зоны", "minecraft:chest", cave, false, true, PolicyId.PROTECTED_BLOCKS),
                row("камень в зоне, поставленный игроком: зона раньше игрока", "minecraft:stone", cave, true, true, PolicyId.PROTECTED_ZONE),
                row("бревно игрока в доме: игрок раньше здания", "minecraft:oak_log", house(), true, false, PolicyId.PLAYER_BUILDINGS)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    void check_Row_RefusesByTheFirstMatchingRule(String name, String block, List<String> neighbours, boolean placed, boolean zone, PolicyId refusedBy) {
        BlockProtection.Decision decision = protection.check(block, neighbours, placed, zone);

        if (refusedBy == null) {
            assertEquals(new BlockProtection.Allowed(), decision, "Строка «" + name + "»: блок природный, ломать можно");
        } else {
            BlockProtection.Refused refused = assertInstanceOf(BlockProtection.Refused.class, decision, "Строка «" + name + "»: ломать нельзя");
            assertEquals(refusedBy, refused.policy(), "Строка «" + name + "»: порядок правил — чёрный список, зона, игрок, здание. Причина: " + refused.reason());
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"minecraft:oak_sign", "minecraft:oak_hanging_sign", "minecraft:birch_door", "minecraft:iron_door", "minecraft:iron_trapdoor",
            "minecraft:oak_fence", "minecraft:nether_brick_fence", "minecraft:oak_fence_gate", "minecraft:stone_pressure_plate", "minecraft:lever",
            "minecraft:stone_button", "minecraft:bell", "minecraft:lectern", "minecraft:jukebox", "minecraft:note_block", "minecraft:redstone_wire",
            "minecraft:redstone_torch", "minecraft:rail", "minecraft:powered_rail", "minecraft:torch", "minecraft:wall_torch", "minecraft:soul_lantern",
            "minecraft:ladder", "minecraft:white_banner", "minecraft:piston", "minecraft:repeater"})
    void check_DenyListBlock_IsRefusedAsProtectedBlock(String block) {
        BlockProtection.Decision decision = protection.check(block, forest(), false, false);

        BlockProtection.Refused refused = assertInstanceOf(BlockProtection.Refused.class, decision, "Блок " + block + " из чёрного списка защиты: его ломать нельзя");
        assertEquals(PolicyId.PROTECTED_BLOCKS, refused.policy(), "Блок " + block + ": отказ по чёрному списку называет политику PROTECTED_BLOCKS");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"minecraft:stone", "minecraft:dirt", "minecraft:grass_block", "minecraft:oak_log", "minecraft:dark_oak_leaves", "minecraft:sand",
            "minecraft:gravel", "minecraft:coal_ore", "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore", "minecraft:nether_quartz_ore",
            "minecraft:deepslate", "minecraft:tuff", "minecraft:netherrack", "minecraft:short_grass", "minecraft:sugar_cane"})
    void check_NaturalBlockInNature_IsAllowed(String block) {
        BlockProtection.Decision decision = protection.check(block, around(0, "", "minecraft:stone"), false, false);

        assertEquals(new BlockProtection.Allowed(), decision, "Природный блок " + block + " вдали от построек обязан ломаться: иначе добыча руды и рубка леса остановятся");
    }

    @Test
    void check_ChestInPlainCave_ReasonComesFromGatherPolicy() {
        BlockProtection.Refused refused = (BlockProtection.Refused) protection.check("minecraft:chest", forest(), false, false);

        assertEquals(((GatherPolicy.Refused) GatherPolicy.check("minecraft:chest")).reason(), refused.reason(),
                "Контейнеры и механизмы запрещает GatherPolicy, protection.json их не дублирует: причина берётся оттуда");
    }

    @Test
    void check_BuildingRefusal_NamesCountAndThreshold() {
        BlockProtection.Refused refused = (BlockProtection.Refused) protection.check("minecraft:oak_log", house(), false, false);

        assertEquals("вокруг блока 'minecraft:oak_log' 14 искусственных блоков из 26 (порог 6, например 'minecraft:oak_planks'): это постройка игрока", refused.reason(),
                "Отказ называет число искусственных соседей и порог: по итогу задачи видно, почему бревно пропущено");
    }

    @Test
    void check_WrongNeighbourCount_ThrowsWithCount() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> protection.check("minecraft:stone", List.of("minecraft:air"), false, false), "25 или 27 соседей — ошибка вызова");

        assertTrue(error.getMessage().startsWith("Соседей 1, ожидается 26"), "Ошибка называет число и ожидание. Фактическое: " + error.getMessage());
    }

    @Test
    void checkOwn_OwnWallInsideOwnBuild_IsAllowed() {
        BlockProtection.Decision refused = protection.check("minecraft:cobblestone", house(), false, false);

        assertInstanceOf(BlockProtection.Refused.class, refused,
                "Предусловие: стена из булыжника среди 14 искусственных соседей считается постройкой игрока и обычной проверкой запрещена");
        assertEquals(new BlockProtection.Allowed(), protection.checkOwn("minecraft:cobblestone", false),
                "Свой поставленный блок дух разбирает: эвристика «постройка игрока» и защита игрока на блоки духов не распространяются, "
                        + "иначе дух не разберёт то, что построил");
    }

    @Test
    void checkOwn_DenyBlockInZone_IsStillRefused() {
        assertInstanceOf(BlockProtection.Refused.class, protection.checkOwn("minecraft:chest", false),
                "Свой сундук всё равно запрещён: чёрный список действует и на блоки духов, иначе дух разберёт механизм");
        assertInstanceOf(BlockProtection.Refused.class, protection.checkOwn("minecraft:cobblestone", true),
                "Своя постройка в защищённой зоне всё равно запрещена: зона сильнее метки духа");
    }

    @Test
    void file_Loaded_ThresholdIsSixAndGatherPolicyIsNotDuplicated() {
        assertEquals(6, protection.buildingThreshold(), "Порог «здания» 6 искусственных соседей из 26 (предварительное значение чеклиста)");
        for (String exact : GatherPolicy.forbiddenExactIds()) {
            assertFalse(protection.denyPatterns().contains("minecraft:" + exact),
                    "protection.json → deny не повторяет точный id '" + exact + "' из GatherPolicy: дубль разъедется при правке одного из списков");
        }
    }
}

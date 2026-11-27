package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.repair.RepairData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RepairAuditPart2Tests {
    private static final BlockCoord ORIGIN = new BlockCoord(10, 64, 10);
    private static final String AIR = "minecraft:air";

    private static RepairAudit.Report repairAudit(Schematic scheme, Map<BlockCoord, String> world) {
        return RepairAudit.audit(scheme, ORIGIN, BuildRotation.NONE, new MapWorld(world), RepairData.defaults());
    }

    private static Schematic scheme() {
        return new Schematic(
                3,
                1,
                1,
                List.of(
                        new Schematic.Entry(0, 0, 0, "cobblestone"),
                        new Schematic.Entry(1, 0, 0, "oak_planks"),
                        new Schematic.Entry(2, 0, 0, "cobblestone")));
    }

    private static Schematic fourBlockScheme() {
        return new Schematic(
                4,
                1,
                1,
                List.of(
                        new Schematic.Entry(0, 0, 0, "cobblestone"),
                        new Schematic.Entry(1, 0, 0, "oak_planks"),
                        new Schematic.Entry(2, 0, 0, "cobblestone"),
                        new Schematic.Entry(3, 0, 0, "oak_planks")));
    }

    private static Map<BlockCoord, String> fourBlockWorld() {
        Map<BlockCoord, String> world = new HashMap<>();
        world.put(ORIGIN, "minecraft:cobblestone");
        world.put(new BlockCoord(11, 64, 10), "minecraft:oak_planks");
        world.put(new BlockCoord(12, 64, 10), "minecraft:cobblestone");
        world.put(new BlockCoord(13, 64, 10), "minecraft:oak_planks");
        return world;
    }

    private record MapWorld(Map<BlockCoord, String> blocks) implements RepairAudit.WorldView {
        @Override
        public String blockAt(BlockCoord cell) {
            return blocks.getOrDefault(cell, AIR);
        }
    }

    @Test
    void audit_UnloadedChunk_UnknownNotDamage() {
        Map<BlockCoord, String> world = new HashMap<>();
        world.put(ORIGIN, AIR);
        world.put(new BlockCoord(11, 64, 10), AIR);
        world.put(new BlockCoord(12, 64, 10), "minecraft:cobblestone");
        Set<BlockCoord> unloaded = Set.of(ORIGIN, new BlockCoord(11, 64, 10));
        RepairAudit.WorldView view = new RepairAudit.WorldView() {
            @Override
            public String blockAt(BlockCoord cell) {
                return world.getOrDefault(cell, AIR);
            }

            @Override
            public boolean loaded(BlockCoord cell) {
                return !unloaded.contains(cell);
            }
        };
        RepairAudit.Report report =
                RepairAudit.audit(scheme(), ORIGIN, BuildRotation.NONE, view, RepairData.defaults());
        assertEquals(
                2,
                report.unknown(),
                "Две клетки в незагруженном чанке — unknown, а не повреждение Иначе тест audit_UnloadedChunk_UnknownNotDamage перестанет ловить эту поломку. При смене audit_UnloadedChunk_UnknownNotDamage проверь это утверждение.");
        assertFalse(
                report.damaged(),
                "По незагруженным чанкам ремонт не начинается: иначе снесёт готовое При смене audit_UnloadedChunk_UnknownNotDamage проверь это утверждение.");
        assertTrue(
                report.fixes().isEmpty(),
                "Правок по неизвестным клеткам нет: их не с чем сверять Иначе тест audit_UnloadedChunk_UnknownNotDamage перестанет ловить эту поломку. При смене audit_UnloadedChunk_UnknownNotDamage проверь это утверждение.");
    }

    @Test
    void audit_MoreThanRebuildPercentMissing_RebuildsWithoutFixes() {
        Map<BlockCoord, String> world = new HashMap<>();
        world.put(ORIGIN, AIR);
        world.put(new BlockCoord(11, 64, 10), AIR);
        world.put(new BlockCoord(12, 64, 10), "minecraft:cobblestone");
        RepairAudit.Report report = repairAudit(scheme(), world);
        assertTrue(
                report.rebuild(),
                "Два блока из трёх — больше 50% из repair.json: это перестройка, не ремонт Иначе тест audit_MoreThanRebuildPercentMissing_RebuildsWithoutFixes перестанет ловить эту поломку. При смене audit_MoreThanRebuildPercentMissing_RebuildsWithoutFixes проверь это утверждение.");
        assertEquals(
                List.of(),
                report.fixes(),
                "У перестройки нет точечных правок: доска заказывает уровень заново Иначе тест audit_MoreThanRebuildPercentMissing_RebuildsWithoutFixes перестанет ловить эту поломку. При смене audit_MoreThanRebuildPercentMissing_RebuildsWithoutFixes проверь это утверждение.");
    }

    @Test
    void audit_ExactlyRebuildPercentMissing_RepairsInsteadOfRebuild() {
        Map<BlockCoord, String> world = fourBlockWorld();
        world.put(ORIGIN, AIR);
        world.put(new BlockCoord(11, 64, 10), AIR);
        RepairAudit.Report report = repairAudit(fourBlockScheme(), world);
        assertEquals(
                2,
                report.missing(),
                "Нет двух блоков из четырёх Иначе тест audit_ExactlyRebuildPercentMissing_RepairsInsteadOfRebuild перестанет ловить эту поломку. При смене audit_ExactlyRebuildPercentMissing_RepairsInsteadOfRebuild проверь это утверждение.");
        assertFalse(
                report.rebuild(),
                "Ровно 50% — не больше порога из repair.json: постройка ремонтируется точечно Иначе тест audit_ExactlyRebuildPercentMissing_RepairsInsteadOfRebuild перестанет ловить эту поломку. При смене audit_ExactlyRebuildPercentMissing_RepairsInsteadOfRebuild проверь это утверждение.");
    }

    @Test
    void audit_ExpectedAirOccupiedByNatural_ClearsIt() {
        Schematic scheme = new Schematic(
                3,
                1,
                1,
                List.of(new Schematic.Entry(0, 0, 0, "cobblestone"), new Schematic.Entry(2, 0, 0, "cobblestone")),
                List.of(new Schematic.Cell(1, 0, 0)));
        Map<BlockCoord, String> world = new HashMap<>();
        world.put(ORIGIN, "minecraft:cobblestone");
        world.put(new BlockCoord(11, 64, 10), "minecraft:dirt");
        world.put(new BlockCoord(12, 64, 10), "minecraft:cobblestone");
        RepairAudit.Report report = repairAudit(scheme, world);
        assertEquals(
                List.of(new RepairAudit.Fix(
                        new BlockCoord(11, 64, 10), RepairAudit.Action.CLEAR, AIR, "minecraft:dirt")),
                report.fixes(),
                "Природный блок там, где схема требует воздух, убирается: иначе проход остаётся завален При смене audit_ExpectedAirOccupiedByNatural_ClearsIt проверь это утверждение.");
    }

    @Test
    void audit_BuildingAtWrongHeight_MissingBelowAndExtraAbove() {
        Schematic scheme = new Schematic(
                3,
                2,
                1,
                List.of(
                        new Schematic.Entry(0, 0, 0, "cobblestone"),
                        new Schematic.Entry(1, 0, 0, "cobblestone"),
                        new Schematic.Entry(2, 0, 0, "cobblestone")),
                List.of(new Schematic.Cell(1, 1, 0)));
        Map<BlockCoord, String> world = new HashMap<>();
        world.put(ORIGIN, AIR);
        world.put(new BlockCoord(11, 64, 10), "minecraft:cobblestone");
        world.put(new BlockCoord(12, 64, 10), "minecraft:cobblestone");
        world.put(new BlockCoord(11, 65, 10), "minecraft:cobblestone");
        RepairAudit.Report report = repairAudit(scheme, world);
        assertTrue(
                report.fixes()
                        .contains(new RepairAudit.Fix(ORIGIN, RepairAudit.Action.PLACE, "minecraft:cobblestone", AIR)),
                "Постройка на другой высоте: на единой высоте клетка пуста — её ставят заново:  Иначе тест audit_BuildingAtWrongHeight_MissingBelowAndExtraAbove перестанет ловить эту поломку. При смене audit_BuildingAtWrongHeight_MissingBelowAndExtraAbove проверь это утверждение."
                        + report.fixes());
        assertTrue(
                report.fixes()
                        .contains(new RepairAudit.Fix(
                                new BlockCoord(11, 65, 10), RepairAudit.Action.CLEAR, AIR, "minecraft:cobblestone")),
                "Блок, стоящий не на единой высоте, становится extra (снос): иначе дом остался бы повисшим:  При смене audit_BuildingAtWrongHeight_MissingBelowAndExtraAbove проверь это утверждение."
                        + report.fixes());
    }

    @Test
    void audit_SoftBlockMissing_SkippedAsNotDamage() {
        Schematic scheme = new Schematic(1, 1, 1, List.of(new Schematic.Entry(0, 0, 0, "poppy")));
        Map<BlockCoord, String> world = new HashMap<>();
        world.put(ORIGIN, AIR);
        RepairAudit.Report report = repairAudit(scheme, world);
        assertEquals(
                0,
                report.missing(),
                "Мягкая клетка из repair.json не считается повреждением Иначе тест audit_SoftBlockMissing_SkippedAsNotDamage перестанет ловить эту поломку. При смене audit_SoftBlockMissing_SkippedAsNotDamage проверь это утверждение.");
        assertEquals(
                List.of(),
                report.fixes(),
                "Цветы и посевы ремонт не требует: их отсутствие — не поломка Иначе тест audit_SoftBlockMissing_SkippedAsNotDamage перестанет ловить эту поломку. При смене audit_SoftBlockMissing_SkippedAsNotDamage проверь это утверждение.");
    }

    @Test
    void defaults_RepairNumbers_ComeFromData() {
        RepairData rules = RepairData.defaults();
        assertEquals(
                256,
                rules.cellBudgetPerTick(),
                "Бюджет обхода — число из repair.json Иначе тест defaults_RepairNumbers_ComeFromData перестанет ловить эту поломку. При смене defaults_RepairNumbers_ComeFromData проверь это утверждение.");
        assertEquals(
                50,
                rules.rebuildPercent(),
                "Порог перестройки — 50% из repair.json Иначе тест defaults_RepairNumbers_ComeFromData перестанет ловить эту поломку. При смене defaults_RepairNumbers_ComeFromData проверь это утверждение.");
        assertEquals(
                95,
                rules.priorities().wall(),
                "Приоритет стены — число из repair.json Иначе тест defaults_RepairNumbers_ComeFromData перестанет ловить эту поломку. При смене defaults_RepairNumbers_ComeFromData проверь это утверждение.");
        assertEquals(
                70,
                rules.priorities().housing(),
                "Приоритет жилья — число из repair.json Иначе тест defaults_RepairNumbers_ComeFromData перестанет ловить эту поломку. При смене defaults_RepairNumbers_ComeFromData проверь это утверждение.");
        assertEquals(
                60,
                rules.priorities().craft(),
                "Приоритет ремесла и склада — число из repair.json Иначе тест defaults_RepairNumbers_ComeFromData перестанет ловить эту поломку. При смене defaults_RepairNumbers_ComeFromData проверь это утверждение.");
        assertEquals(
                30,
                rules.priorities().decor(),
                "Приоритет декора — число из repair.json Иначе тест defaults_RepairNumbers_ComeFromData перестанет ловить эту поломку. При смене defaults_RepairNumbers_ComeFromData проверь это утверждение.");
    }
}

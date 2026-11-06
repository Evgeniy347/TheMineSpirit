package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.repair.RepairData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RepairAuditTests {
    private static final BlockCoord ORIGIN = new BlockCoord(10, 64, 10);
    private static final String AIR = "minecraft:air";

    @Test
    void audit_MissingBlock_PlacesBySchemeAndRepairsToIt() {
        Map<BlockCoord, String> world = new HashMap<>();
        world.put(ORIGIN, AIR);
        world.put(new BlockCoord(11, 64, 10), "minecraft:oak_planks");
        world.put(new BlockCoord(12, 64, 10), "minecraft:cobblestone");

        RepairAudit.Report report = repairAudit(scheme(), world, Set.of(), Map.of(), 100);

        assertEquals(3, report.total(), "В схеме три обязательных блока: по ним считается доля повреждения");
        assertEquals(1, report.missing(), "Нет одного блока из трёх: воздух вместо булыжника");
        assertFalse(report.rebuild(), "Одного блока из трёх мало для перестройки: порог из repair.json выше");
        assertEquals(
                List.of(new RepairAudit.Fix(ORIGIN, RepairAudit.Action.PLACE, "minecraft:cobblestone", AIR, 0)),
                report.fixes(),
                "Аудит обязан вернуть клетку и блок схемы: без этого ремонт не знает, что ставить");

        for (RepairAudit.Fix fix : report.fixes()) {
            world.put(fix.cell(), fix.wanted());
        }
        assertEquals(
                List.of(),
                repairAudit(scheme(), world, Set.of(), Map.of(), 100).fixes(),
                "Ремонт возвращает постройку к схеме: после правок мир совпадает со схемой");
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
            public boolean playerPlaced(BlockCoord cell) {
                return false;
            }

            @Override
            public boolean loaded(BlockCoord cell) {
                return !unloaded.contains(cell);
            }
        };

        RepairAudit.Report report =
                RepairAudit.audit(scheme(), ORIGIN, BuildRotation.NONE, view, RepairData.defaults(), Map.of(), 100);

        assertEquals(2, report.unknown(), "Две клетки в незагруженном чанке — unknown, а не повреждение");
        assertFalse(report.damaged(), "По незагруженным чанкам ремонт не начинается: иначе снесёт готовое");
        assertTrue(report.fixes().isEmpty(), "Правок по неизвестным клеткам нет: их не с чем сверять");
    }

    @Test
    void audit_PlayerBlock_WarnsForWarnTicksThenRemoves() {
        Map<BlockCoord, String> world = new HashMap<>();
        world.put(ORIGIN, "minecraft:dirt");
        world.put(new BlockCoord(11, 64, 10), "minecraft:oak_planks");
        world.put(new BlockCoord(12, 64, 10), "minecraft:cobblestone");
        Map<BlockCoord, Long> firstSeen = new HashMap<>();
        firstSeen.put(ORIGIN, 1000L);

        RepairAudit.Report early = repairAudit(scheme(), world, Set.of(ORIGIN), firstSeen, 4599);

        assertEquals(
                List.of(new RepairAudit.Fix(
                        ORIGIN, RepairAudit.Action.WARN, "minecraft:cobblestone", "minecraft:dirt", 4600)),
                early.fixes(),
                "Блок игрока не убирается сразу: 3600 тиков из данных — три минуты на уборку");

        RepairAudit.Report due = repairAudit(scheme(), world, Set.of(ORIGIN), firstSeen, 4600);

        assertEquals(
                List.of(new RepairAudit.Fix(
                        ORIGIN, RepairAudit.Action.REMOVE, "minecraft:cobblestone", "minecraft:dirt", 4600)),
                due.fixes(),
                "Через три минуты предупреждения блок игрока убирается: постройка возвращается к схеме");
    }

    @Test
    void audit_MoreThanRebuildPercentMissing_RebuildsWithoutFixes() {
        Map<BlockCoord, String> world = new HashMap<>();
        world.put(ORIGIN, AIR);
        world.put(new BlockCoord(11, 64, 10), AIR);
        world.put(new BlockCoord(12, 64, 10), "minecraft:cobblestone");

        RepairAudit.Report report = repairAudit(scheme(), world, Set.of(), Map.of(), 100);

        assertTrue(report.rebuild(), "Два блока из трёх — больше 50% из repair.json: это перестройка, не ремонт");
        assertEquals(List.of(), report.fixes(), "У перестройки нет точечных правок: доска заказывает уровень заново");
    }

    @Test
    void audit_ExactlyRebuildPercentMissing_RepairsInsteadOfRebuild() {
        Map<BlockCoord, String> world = fourBlockWorld();
        world.put(ORIGIN, AIR);
        world.put(new BlockCoord(11, 64, 10), AIR);

        RepairAudit.Report report = repairAudit(fourBlockScheme(), world, Set.of(), Map.of(), 100);

        assertEquals(2, report.missing(), "Нет двух блоков из четырёх");
        assertFalse(report.rebuild(), "Ровно 50% — не больше порога из repair.json: постройка ремонтируется точечно");
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

        RepairAudit.Report report = repairAudit(scheme, world, Set.of(), Map.of(), 100);

        assertEquals(
                List.of(new RepairAudit.Fix(
                        new BlockCoord(11, 64, 10), RepairAudit.Action.CLEAR, AIR, "minecraft:dirt", 0)),
                report.fixes(),
                "Природный блок там, где схема требует воздух, убирается: иначе проход остаётся завален");
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

        RepairAudit.Report report = repairAudit(scheme, world, Set.of(), Map.of(), 100);

        assertTrue(
                report.fixes()
                        .contains(
                                new RepairAudit.Fix(ORIGIN, RepairAudit.Action.PLACE, "minecraft:cobblestone", AIR, 0)),
                "Постройка на другой высоте: на единой высоте клетка пуста — её ставят заново: " + report.fixes());
        assertTrue(
                report.fixes()
                        .contains(new RepairAudit.Fix(
                                new BlockCoord(11, 65, 10), RepairAudit.Action.CLEAR, AIR, "minecraft:cobblestone", 0)),
                "Блок, стоящий не на единой высоте, становится extra (снос): иначе дом остался бы повисшим: "
                        + report.fixes());
    }

    @Test
    void audit_SoftBlockMissing_SkippedAsNotDamage() {
        Schematic scheme = new Schematic(1, 1, 1, List.of(new Schematic.Entry(0, 0, 0, "poppy")));
        Map<BlockCoord, String> world = new HashMap<>();
        world.put(ORIGIN, AIR);

        RepairAudit.Report report = repairAudit(scheme, world, Set.of(), Map.of(), 100);

        assertEquals(0, report.missing(), "Мягкая клетка из repair.json не считается повреждением");
        assertEquals(List.of(), report.fixes(), "Цветы и посевы ремонт не требует: их отсутствие — не поломка");
    }

    @Test
    void defaults_AuditNumbers_ComeFromData() {
        RepairData rules = RepairData.defaults();

        assertEquals(24000, rules.auditIntervalTicks(), "Плановый обход — раз в игровой день из repair.json");
        assertEquals(256, rules.cellBudgetPerTick(), "Бюджет обхода — число из repair.json");
        assertEquals(3600, rules.warnTicks(), "Предупреждение игроку — три минуты (3600 тиков) из данных");
        assertEquals(1200, rules.warnRepeatTicks(), "Напоминание — раз в минуту (1200 тиков) из данных");
        assertEquals(50, rules.rebuildPercent(), "Порог перестройки — 50% из repair.json");
        assertEquals(95, rules.priorities().wall(), "Приоритет стены — число из repair.json");
        assertEquals(70, rules.priorities().housing(), "Приоритет жилья — число из repair.json");
        assertEquals(60, rules.priorities().craft(), "Приоритет ремесла и склада — число из repair.json");
        assertEquals(30, rules.priorities().decor(), "Приоритет декора — число из repair.json");
    }

    private static RepairAudit.Report repairAudit(
            Schematic scheme,
            Map<BlockCoord, String> world,
            Set<BlockCoord> player,
            Map<BlockCoord, Long> firstSeen,
            long now) {
        return RepairAudit.audit(
                scheme, ORIGIN, BuildRotation.NONE, new MapWorld(world, player), RepairData.defaults(), firstSeen, now);
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

    private record MapWorld(Map<BlockCoord, String> blocks, Set<BlockCoord> player) implements RepairAudit.WorldView {
        @Override
        public String blockAt(BlockCoord cell) {
            return blocks.getOrDefault(cell, AIR);
        }

        @Override
        public boolean playerPlaced(BlockCoord cell) {
            return player.contains(cell);
        }
    }
}

package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SchematicTextParser;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.repair.RepairData;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TownPlanChangeGuardTests {
    private static final TownRules RULES = TownRules.load();
    private static final List<List<String>> STAGES = StageRules.load().plans();
    private static final BlockCoord ANCHOR = new BlockCoord(200, 70, -300);
    private static final int RADIUS = RULES.ground().probeRadius();

    @Test
    void wheatField_PlacedAsPlanPlot_WithFiveByTenFarmAndWater() {
        TownPlan plan = plan();
        Placement field = plan.placement("wheat_field")
                .orElseThrow(() -> new AssertionError("Домашняя грядка не получила участок: " + plan.unplaced()));

        assertEquals(7, field.width(), "Участок грядки — 5×10 пашни плюс обход по краю по одному блоку");
        assertEquals(12, field.depth(), "Участок грядки — 5×10 пашни плюс обход по краю по одному блоку");
        assertTrue(plan.region().contains(field.x(), field.z()), "Грядка стоит внутри контура поселения");
        for (ColumnMask.Cell cell : field.mask().cells()) {
            assertTrue(plan.onPlot(cell.x(), cell.z()), "Клетка грядки " + cell + " вышла за контур поселения");
            assertFalse(plan.streets().contains(cell.x(), cell.z()), "Грядка налезла на улицу в " + cell);
        }
        for (Placement other : plan.placements()) {
            assertFalse(
                    !other.equals(field)
                            && other.overlaps(field, RULES.spacing().gap()),
                    "Грядка пересекает участок '" + other.id() + "'");
        }
    }

    @Test
    void wheatFieldScheme_AllFarmland_EveryCellWithinFourOfWater() {
        Schematic scheme = scheme("wheat_field_1");
        BlockCoord origin = new BlockCoord(0, 0, 0);
        List<Schematic.PlacedEntry> entries = scheme.entries(origin);
        List<BlockCoord> water = entries.stream()
                .filter(entry -> "water".equals(SchematicBlocks.path(entry.block())))
                .map(Schematic.PlacedEntry::pos)
                .toList();
        List<BlockCoord> farm = entries.stream()
                .filter(entry -> "farmland".equals(SchematicBlocks.path(entry.block())))
                .map(Schematic.PlacedEntry::pos)
                .toList();

        assertEquals(2, water.size(), "На 5×10 пашни хватает двух клеток воды: каждая увлажняет 9 клеток в длину");
        assertEquals(48, farm.size(), "5×10 минус две клетки воды — 48 грядок");
        for (BlockCoord cell : farm) {
            assertTrue(
                    water.stream().anyMatch(at -> Math.abs(at.x() - cell.x()) <= 4 && Math.abs(at.z() - cell.z()) <= 4),
                    "Грядка " + cell + " не увлажнена: до воды больше четырёх блоков");
        }
        for (BlockCoord cell : water) {
            assertTrue(
                    cell.x() > 0 && cell.x() < 6 && cell.z() > 0 && cell.z() < 11,
                    "Вода " + cell + " стоит внутри контура 5×10, а не на обходе");
        }
        for (Schematic.PlacedEntry entry : entries) {
            if ("dirt_path".equals(SchematicBlocks.path(entry.block()))) {
                assertTrue(
                        entry.pos().x() == 0
                                || entry.pos().x() == 6
                                || entry.pos().z() == 0
                                || entry.pos().z() == 11,
                        "Обход " + entry.pos() + " обязан идти по краю участка");
            }
        }
        assertEquals(5 * 10 - 2, farm.size(), "Пашня — прямоугольник 5×10 без клеток воды: иначе вода занимает грядку");
    }

    @Test
    void readapt_PlacementsOutsideCatalog_DoNotBreakStreetOpening() {
        TownPlan base = plan();
        Placement any = base.placements().get(0);
        List<Placement> placements = new ArrayList<>(base.placements());
        for (String id : List.of("orchard_9", "retired_lot")) {
            placements.add(new Placement(
                    id, id, any.stage(), any.x() + 40, any.z() + 40, any.width(), any.depth(), any.facing()));
        }
        TownPlan stored =
                new TownPlan(base.anchor(), base.region(), base.streets(), placements, base.fence(), base.unplaced());
        TownRules changed = withSize(any.id(), 2, 2);
        Set<String> built = new HashSet<>();
        placements.stream()
                .map(Placement::id)
                .filter(id -> !id.equals(any.id()))
                .forEach(built::add);

        TownPlan next = TownPlanner.readapt(grid(), stored, changed, built);

        assertTrue(next.placement("orchard_9").isPresent(), "сад по спросу не из каталога остаётся в плане");
        assertTrue(next.placement("retired_lot").isPresent(), "бывший участок не роняет подгонку и весь граф");
    }

    @Test
    void change_AnyOneSize_KeepsOtherBuiltPlacementsAndStreets() {
        TownPlan base = plan();
        for (Placement target : base.placements()) {
            TownRules changed = withSize(target.id(), 2, 2);
            Set<String> built = new HashSet<>();
            base.placements().stream()
                    .filter(place -> !place.id().equals(target.id()))
                    .map(Placement::id)
                    .forEach(built::add);
            TownPlan next = TownPlanner.readapt(grid(), base, changed, built);
            Placement resized = next.placement(target.id())
                    .orElseThrow(() -> new AssertionError(
                            "После смены размера '" + target.id() + "' потерялся участок: " + next.unplaced()));
            TownEntry wanted = changed.entry(target.id()).orElseThrow();
            boolean fitsNew = (resized.width() == wanted.width() && resized.depth() == wanted.depth())
                    || (resized.width() == wanted.depth() && resized.depth() == wanted.width());
            assertTrue(
                    fitsNew || resized.equals(target),
                    "Участок '" + target.id() + "' после смены размера обязан либо получить новый размер "
                            + wanted.width() + "×" + wanted.depth() + ", либо остаться прежним: " + resized);
            for (Placement before : base.placements()) {
                if (before.id().equals(target.id())) {
                    continue;
                }
                Placement after = next.placement(before.id())
                        .orElseThrow(() -> new AssertionError(
                                "Смена размера '" + target.id() + "' потеряла чужой участок '" + before.id() + "'"));
                assertEquals(
                        before,
                        after,
                        "Смена размера '" + target.id() + "' сдвинула готовый участок '" + before.id() + "'");
            }
            assertPlacementsApart(next);
        }
    }

    @Test
    void savedPlanWithoutWheat_Migration_KeepsEveryOtherPlot() {
        TownPlan base = plan();
        TownPlan old = new TownPlan(
                base.anchor(),
                base.region(),
                base.streets(),
                base.placements().stream()
                        .filter(place -> !"wheat_field".equals(place.id()))
                        .toList(),
                base.fence(),
                base.unplaced());
        TownPlan decoded = TownPlanCodec.decode(TownPlanCodec.encode(old));

        TownPlan grown = TownPlanner.plant(grid(), decoded, RULES, STAGES, List.of(), 1);

        assertTrue(grown.placement("wheat_field").isPresent(), "Старый план обязан получить участок грядки");
        for (Placement before : decoded.placements()) {
            assertEquals(
                    before,
                    grown.placement(before.id()).orElseThrow(),
                    "Переезд грядки не имеет права двигать чужой участок '" + before.id() + "'");
        }
        assertEquals(decoded.anchor(), grown.anchor(), "Origin поселения при миграции не меняется");
        assertPlacementsApart(grown);
    }

    @Test
    void change_AnyOneScheme_KeepsOtherSchemeAudit() {
        Schematic first = scheme("wheat_field_1");
        Schematic second = scheme("field_1");
        Map<BlockCoord, String> world = new HashMap<>();
        for (Schematic.PlacedEntry entry : second.entries(ANCHOR)) {
            world.put(entry.pos(), entry.block());
        }
        int before = audit(second, world).total();

        assertTrue(
                first.sizeX() != second.sizeX() || first.sizeZ() != second.sizeZ(),
                "Схемы обязаны отличаться габаритом: иначе тест не меняет чужую схему");
        assertEquals(before, audit(second, world).total(), "Чужая схема не меняет аудит этой постройки");
        assertEquals(
                before,
                audit(second, world).total(),
                "Повторный аудит после смены другой схемы обязан дать тот же результат");
    }

    private static TownPlan plan() {
        return TownPlanner.plan(grid(), ANCHOR, RULES, STAGES);
    }

    private static TerrainGrid grid() {
        int side = 2 * RADIUS + 1;
        int[] heights = new int[side * side];
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                heights[TerrainGrid.index(RADIUS, dx, dz)] = 70;
            }
        }
        return TerrainGrid.around(ANCHOR, RADIUS, heights, new BitSet());
    }

    private static TownRules withSize(String id, int addWidth, int addDepth) {
        List<TownEntry> entries = new ArrayList<>();
        for (TownEntry entry : RULES.buildings()) {
            if (entry.id().equals(id)) {
                entries.add(new TownEntry(
                        entry.id(),
                        entry.name(),
                        entry.width() + addWidth,
                        entry.depth() + addDepth,
                        entry.quarter(),
                        entry.level(),
                        entry.rotate()));
            } else {
                entries.add(entry);
            }
        }
        return new TownRules(RULES.ground(), RULES.streets(), RULES.spacing(), entries);
    }

    private static void assertPlacementsApart(TownPlan plan) {
        for (Placement place : plan.placements()) {
            for (Placement other : plan.placements()) {
                assertFalse(
                        !other.equals(place)
                                && other.overlaps(place, RULES.spacing().gap()),
                        "После смены размера участки '" + place.id() + "' и '" + other.id() + "' пересеклись");
            }
            for (ColumnMask.Cell cell : place.mask().cells()) {
                assertFalse(
                        plan.streets().contains(cell.x(), cell.z()),
                        "После смены размера участок '" + place.id() + "' налез на улицу в " + cell);
            }
        }
    }

    private static Schematic scheme(String id) {
        String path = PresetLibrary.SCHEMATICS_ROOT + id + SchematicTextParser.EXTENSION;
        return SchematicTextParser.parse(JsonResource.readClasspath(path), path).schematic();
    }

    private static RepairAudit.Report audit(Schematic scheme, Map<BlockCoord, String> world) {
        return RepairAudit.audit(
                scheme,
                ANCHOR,
                BuildRotation.NONE,
                new RepairAudit.WorldView() {
                    @Override
                    public String blockAt(BlockCoord cell) {
                        return world.getOrDefault(cell, "air");
                    }

                    @Override
                    public boolean playerPlaced(BlockCoord cell) {
                        return false;
                    }
                },
                RepairData.defaults(),
                Map.of(),
                100);
    }
}

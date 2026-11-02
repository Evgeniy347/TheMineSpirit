package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.building.FenceGateRule;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class TownPlannerTests {
    private static final TownRules RULES = TownRules.load();
    private static final List<List<String>> STAGES = StageRules.load().plans();
    private static final BlockCoord ANCHOR = new BlockCoord(200, 70, -300);
    private static final int RADIUS = RULES.ground().probeRadius();

    @Test
    void plan_FlatLand_AreaNearTargetAndStageOnePlaced() {
        TownPlan plan = plan((x, z) -> 70, (x, z) -> 0);

        int target = RULES.ground().area();
        assertTrue(
                Math.abs(plan.area() - target) <= target / 10,
                "Участок на ровной суше — около " + target + " колонок по решению владельца 25.10, а вышло "
                        + plan.area() + ": проверь рост контура и сглаживание");
        for (String id : STAGES.get(0)) {
            assertTrue(
                    TownPlanner.LINE_ELEMENTS.contains(id) || plan.placement(id).isPresent(),
                    "Постройка этапа 1 '" + id + "' без места в плане: этап 1 берёт места только из плана; "
                            + "не поместилось " + plan.unplaced());
        }
    }

    @Test
    void plan_FlatLand_PlacementsInsideFreeAndApart() {
        TownPlan plan = plan((x, z) -> 70, (x, z) -> 0);

        List<Placement> places = plan.placements();
        for (Placement place : places) {
            for (ColumnMask.Cell cell : place.mask().cells()) {
                assertTrue(
                        plan.onPlot(cell.x(), cell.z()),
                        "Место '" + place.id() + "' выходит за контур в " + cell + ": постройки стоят внутри ограды");
                boolean centered = RULES.entry(place.id()).orElseThrow().quarter() == TownEntry.Quarter.CENTER;
                assertFalse(
                        !centered && plan.streets().contains(cell.x(), cell.z()),
                        "Место '" + place.id() + "' лежит на улице в " + cell + ": улица — общий проход");
            }
            for (Placement other : places) {
                assertFalse(
                        !other.equals(place)
                                && other.overlaps(place, RULES.spacing().gap()),
                        "Места '" + place.id() + "' и '" + other.id() + "' ближе отступа "
                                + RULES.spacing().gap() + ": между участками газон с тропой, наложение ломает стройку");
            }
        }
    }

    @Test
    void plan_SameTerrain_SamePlan() {
        IntBinaryOperator hills = (x, z) -> 70 + (int) Math.round(3 * Math.sin(x / 9.0) + 2 * Math.cos(z / 7.0));

        assertEquals(
                plan(hills, (x, z) -> 0),
                plan(hills, (x, z) -> 0),
                "План — чистая функция рельефа: тот же рельеф обязан дать тот же план, иначе живой мир и тест разойдутся");
    }

    @Test
    void codec_EncodedPlan_DecodesEqual() {
        TownPlan plan = plan((x, z) -> 70 + (x * 7 + z * 3) % 3, (x, z) -> 0);

        assertEquals(
                plan,
                TownPlanCodec.decode(TownPlanCodec.encode(plan)),
                "План хранится в мире текстом: прочитанный план обязан совпасть с записанным, иначе после перезапуска "
                        + "этапы возьмут другие места");
    }

    @Test
    void plan_RiverEast_ContourStopsAtWater() {
        int river = ANCHOR.x() + 25;
        TownPlan plan = plan((x, z) -> 70, (x, z) -> Math.abs(x - river) <= 2 ? 1 : 0);

        for (ColumnMask.Cell cell : plan.region().cells()) {
            assertTrue(
                    cell.x() < river - 2,
                    "Клетка " + cell + " за рекой у x=" + river + " вошла в участок: вода — естественная граница");
        }
    }

    @Test
    void plan_CliffNorth_ContourStopsAtCliff() {
        int cliff = ANCHOR.z() - 20;
        TownPlan plan = plan((x, z) -> z < cliff ? 80 : 70, (x, z) -> 0);

        for (ColumnMask.Cell cell : plan.region().cells()) {
            assertTrue(
                    cell.z() >= cliff,
                    "Клетка " + cell + " за обрывом у z=" + cliff + " вошла в участок: обрыв — естественная граница");
        }
    }

    @Test
    void fence_FlatLand_ClosedAndGatesEvenlySpaced() {
        TownPlan plan = plan((x, z) -> 70 + (int) Math.round(3 * Math.sin(x / 9.0)), (x, z) -> 0);
        FenceLine fence = plan.fence();

        List<ColumnMask.Cell> posts = fence.posts();
        for (int index = 0; index < posts.size(); index++) {
            ColumnMask.Cell here = posts.get(index);
            ColumnMask.Cell next = posts.get((index + 1) % posts.size());
            assertEquals(
                    1,
                    Math.abs(here.x() - next.x()) + Math.abs(here.z() - next.z()),
                    "Столбы " + here + " и " + next + " не соседи по стороне: между ними щель, ограда проходима");
        }
        assertFalse(
                escapes(fence),
                "Из-за ограды есть путь к центру мимо столбов: ограда по контуру обязана быть замкнутой");
        for (int span : FenceGates.spans(fence)) {
            assertTrue(
                    span >= FenceGateRule.MIN_SPAN - 3 && span <= FenceGateRule.MAX_SPAN,
                    "Пролёт между калитками " + span + " вне " + FenceGateRule.MIN_SPAN + ".."
                            + FenceGateRule.MAX_SPAN + " (" + FenceGates.spans(fence) + "): калитки равномерно "
                            + "по всем сторонам, иначе обход до калитки слишком долгий");
        }
    }

    @Test
    void plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled() {
        TownPlan base = plan((x, z) -> 70, (x, z) -> 0);
        TownPlan grown = TownPlanner.plant(
                grid((x, z) -> 70, (x, z) -> 0),
                base,
                RULES,
                STAGES,
                List.of(OrchardSites.siteId("orchard_jungle")),
                1);
        Placement extra = grown.placement(OrchardSites.siteId("orchard_jungle"))
                .orElseThrow(() -> new AssertionError("Сад джунглей не появился в плане: " + grown.unplaced()));
        assertTrue(
                grown.placement(StageSites.ORCHARD_EXTRA).isEmpty(),
                "Готовое место orchard_extra занято породой, а не осталось вторым садом-двойником");
        for (Placement other : grown.placements()) {
            assertFalse(
                    !other.equals(extra)
                            && other.overlaps(extra, RULES.spacing().gap()),
                    "Сад джунглей пересекает '" + other.id() + "': у каждой породы своё место");
        }
        StageSite site = StageSites.of(grown, 1, grown.anchor().y(), RULES);
        assertTrue(
                site.inside(extra.x(), extra.z()) && site.inside(extra.x() + extra.width() - 1, extra.z()),
                "Сад джунглей " + extra + " оказался вне ограды этапа: всё поселение внутри контура");
        assertTrue(StageSites.insideEnclosure(site), "Каждый участок плана этапа обязан быть внутри ограды");
        for (ColumnMask.Cell cell : extra.mask().cells()) {
            assertTrue(
                    site.level().contains(cell.x(), cell.z()) || site.mineColumn(cell.x(), cell.z()),
                    "Клетка " + cell + " сада не выравнивается: деревья повиснут или утонут");
        }
        assertEquals(
                "jungle",
                site.extraOrchard("orchard_jungle").orElseThrow().wood(),
                "Порода сада берётся из постройки графа orchard_jungle");
    }

    @Test
    void plant_Extension_KeepsExistingPlotOrigins() {
        TownPlan base = plan((x, z) -> 70, (x, z) -> 0);
        Placement before = base.placement(StageSites.ORCHARD).orElseThrow();

        TownPlan grown = TownPlanner.plant(
                grid((x, z) -> 70, (x, z) -> 0),
                base,
                RULES,
                STAGES,
                List.of(OrchardSites.siteId("orchard_jungle")),
                1);
        Placement after = grown.placement(StageSites.ORCHARD).orElseThrow();

        assertEquals(
                before,
                after,
                "Рост плана ради сада другой породы не переносит уже размеченный сад: одна постройка — один origin");
    }

    @Test
    void plan_ExtensionGrowsFence_OldPlotsStayInside() {
        TownPlan base = plan((x, z) -> 70, (x, z) -> 0);
        TownPlan old = new TownPlan(
                base.anchor(),
                base.region(),
                base.streets(),
                base.placements().stream()
                        .filter(place -> !StageSites.ORCHARD_EXTRA.equals(place.id()))
                        .toList(),
                base.fence(),
                base.unplaced());
        StageSite before = StageSites.of(old, 1, old.anchor().y(), RULES);
        TownPlan grown = TownPlanner.plant(
                grid((x, z) -> 70, (x, z) -> 0), old, RULES, STAGES, List.of(OrchardSites.siteId("orchard_jungle")), 1);
        StageSite after = StageSites.of(grown, 1, grown.anchor().y(), RULES);

        assertTrue(
                after.enclosure().count() >= before.enclosure().count(),
                "Контур ограды при росте плана не вырос: новый сад остался бы за забором");
        assertTrue(StageSites.insideEnclosure(after), "После роста плана все старые и новые участки внутри ограды");
        assertTrue(
                after.ring().posts().containsAll(before.ring().posts())
                        || after.enclosure().count() > before.enclosure().count(),
                "Ограда этапа не охватывает старый и новый контур: старые куски остались бы за забором");
    }

    @Test
    void stages_SecondStagePlan_AllBuildingsRequired() {
        StageRules.Stage second = StageRules.load().stages().get(1);

        assertEquals(
                Set.copyOf(second.plan()),
                second.requires().keySet(),
                "Этап 2 закрывается всем составом плана: у каждой постройки плана есть требование и уровень");
        assertTrue(
                second.requires().values().stream().allMatch(level -> level == 1),
                "Постройки плана этапа 2 стоят уровнем 1 из каталога: завышенный уровень держал бы этап навсегда");
    }

    @Test
    void stages_EveryCatalogEntry_InExactlyOneStage() {
        Set<String> seen = new HashSet<>();
        for (List<String> stage : STAGES) {
            for (String id : stage) {
                assertTrue(
                        seen.add(id),
                        "Постройка '" + id + "' в двух этапах stages.json: место в плане одно, этап — один");
            }
        }
        List<String> orphans = new ArrayList<>();
        RULES.buildings().stream()
                .map(TownEntry::id)
                .filter(id -> !seen.contains(id))
                .forEach(orphans::add);
        assertEquals(
                List.of(),
                orphans,
                "Постройки каталога town.json без этапа: план размечает все постройки всех этапов сразу");
    }

    static TownPlan plan(IntBinaryOperator feet, IntBinaryOperator water) {
        return TownPlanner.plan(grid(feet, water), ANCHOR, RULES, STAGES);
    }

    static TerrainGrid grid(IntBinaryOperator feet, IntBinaryOperator water) {
        int side = 2 * RADIUS + 1;
        int[] heights = new int[side * side];
        BitSet wet = new BitSet(side * side);
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                int x = ANCHOR.x() + dx;
                int z = ANCHOR.z() + dz;
                heights[TerrainGrid.index(RADIUS, dx, dz)] = feet.applyAsInt(x, z);
                wet.set(TerrainGrid.index(RADIUS, dx, dz), water.applyAsInt(x, z) != 0);
            }
        }
        return TerrainGrid.around(ANCHOR, RADIUS, heights, wet);
    }

    private static boolean escapes(FenceLine fence) {
        Set<ColumnMask.Cell> wall = new HashSet<>(fence.posts());
        int edge = RADIUS + 2;
        Deque<ColumnMask.Cell> queue = new ArrayDeque<>();
        Set<ColumnMask.Cell> seen = new HashSet<>();
        ColumnMask.Cell start = new ColumnMask.Cell(ANCHOR.x() - edge, ANCHOR.z() - edge);
        queue.add(start);
        seen.add(start);
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            ColumnMask.Cell cell = queue.poll();
            if (cell.x() == ANCHOR.x() && cell.z() == ANCHOR.z()) {
                return true;
            }
            for (int[] step : steps) {
                ColumnMask.Cell next = new ColumnMask.Cell(cell.x() + step[0], cell.z() + step[1]);
                boolean inBox = Math.abs(next.x() - ANCHOR.x()) <= edge && Math.abs(next.z() - ANCHOR.z()) <= edge;
                if (inBox && !wall.contains(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }
}

package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.building.FenceGateRule;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class TownPlannerTests {
    static final TownRules RULES = TownRules.load();
    static final List<List<String>> STAGES = StageRules.load().plans();
    static final BlockCoord ANCHOR = new BlockCoord(200, 70, -300);
    static final int RADIUS = RULES.ground().probeRadius();

    @Test
    void plan_FlatLand_AreaNearTargetAndStageOnePlaced() {
        TownPlan plan = plan((x, z) -> 70, (x, z) -> 0);

        int target = RULES.ground().area();
        assertTrue(
                Math.abs(plan.area() - target) <= target / 10,
                "Участок на ровной суше — около " + target + " колонок по решению владельца 25.10, а вышло "
                        + plan.area() + ": проверь рост контура и сглаживание"
                        + " Иначе тест plan_FlatLand_AreaNearTargetAndStageOnePlaced перестанет ловить эту поломку. При смене plan_FlatLand_AreaNearTargetAndStageOnePlaced проверь это утверждение.");
        for (String id : STAGES.get(0)) {
            assertTrue(
                    TownPlanner.LINE_ELEMENTS.contains(id) || plan.placement(id).isPresent(),
                    "Постройка этапа 1 '" + id + "' без места в плане: этап 1 берёт места только из плана; "
                            + "не поместилось " + plan.unplaced()
                            + " Иначе тест plan_FlatLand_AreaNearTargetAndStageOnePlaced перестанет ловить эту поломку. При смене plan_FlatLand_AreaNearTargetAndStageOnePlaced проверь это утверждение.");
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
                        "Место '" + place.id() + "' выходит за контур в " + cell + ": постройки стоят внутри ограды"
                                + " Иначе тест plan_FlatLand_PlacementsInsideFreeAndApart перестанет ловить эту поломку. При смене plan_FlatLand_PlacementsInsideFreeAndApart проверь это утверждение.");
                boolean centered = RULES.entry(place.id()).orElseThrow().quarter() == TownEntry.Quarter.CENTER;
                assertFalse(
                        !centered && plan.streets().contains(cell.x(), cell.z()),
                        "Место '" + place.id() + "' лежит на улице в " + cell + ": улица — общий проход"
                                + " Иначе тест plan_FlatLand_PlacementsInsideFreeAndApart перестанет ловить эту поломку. При смене plan_FlatLand_PlacementsInsideFreeAndApart проверь это утверждение.");
            }
            for (Placement other : places) {
                assertFalse(
                        !other.equals(place)
                                && other.overlaps(place, RULES.spacing().gap()),
                        "Места '" + place.id() + "' и '" + other.id() + "' ближе отступа "
                                + RULES.spacing().gap() + ": между участками газон с тропой, наложение ломает стройку"
                                + " При смене plan_FlatLand_PlacementsInsideFreeAndApart проверь это утверждение.");
            }
        }
    }

    @Test
    void plan_SameTerrain_SamePlan() {
        IntBinaryOperator hills = (x, z) -> 70 + (int) Math.round(3 * Math.sin(x / 9.0) + 2 * Math.cos(z / 7.0));

        assertEquals(
                plan(hills, (x, z) -> 0),
                plan(hills, (x, z) -> 0),
                "План — чистая функция рельефа: тот же рельеф обязан дать тот же план, иначе живой мир и тест разойдутся"
                        + " При смене plan_SameTerrain_SamePlan проверь это утверждение.");
    }

    @Test
    void codec_EncodedPlan_DecodesEqual() {
        TownPlan plan = plan((x, z) -> 70 + (x * 7 + z * 3) % 3, (x, z) -> 0);

        assertEquals(
                plan,
                TownPlanCodec.decode(TownPlanCodec.encode(plan)),
                "План хранится в мире текстом: прочитанный план обязан совпасть с записанным, иначе после перезапуска "
                        + "этапы возьмут другие места"
                        + " При смене codec_EncodedPlan_DecodesEqual проверь это утверждение.");
    }

    @Test
    void plan_RiverEast_ContourStopsAtWater() {
        int river = ANCHOR.x() + 25;
        TownPlan plan = plan((x, z) -> 70, (x, z) -> Math.abs(x - river) <= 2 ? 1 : 0);

        for (ColumnMask.Cell cell : plan.region().cells()) {
            assertTrue(
                    cell.x() < river - 2,
                    "Клетка " + cell + " за рекой у x=" + river + " вошла в участок: вода — естественная граница"
                            + " Иначе тест plan_RiverEast_ContourStopsAtWater перестанет ловить эту поломку. При смене plan_RiverEast_ContourStopsAtWater проверь это утверждение.");
        }
    }

    @Test
    void plan_CliffNorth_ContourStopsAtCliff() {
        int cliff = ANCHOR.z() - 20;
        TownPlan plan = plan((x, z) -> z < cliff ? 80 : 70, (x, z) -> 0);

        for (ColumnMask.Cell cell : plan.region().cells()) {
            assertTrue(
                    cell.z() >= cliff,
                    "Клетка " + cell + " за обрывом у z=" + cliff + " вошла в участок: обрыв — естественная граница"
                            + " Иначе тест plan_CliffNorth_ContourStopsAtCliff перестанет ловить эту поломку. При смене plan_CliffNorth_ContourStopsAtCliff проверь это утверждение.");
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
                    "Столбы " + here + " и " + next + " не соседи по стороне: между ними щель, ограда проходима"
                            + " Иначе тест fence_FlatLand_ClosedAndGatesEvenlySpaced перестанет ловить эту поломку. При смене fence_FlatLand_ClosedAndGatesEvenlySpaced проверь это утверждение.");
        }
        assertFalse(
                escapes(fence),
                "Из-за ограды есть путь к центру мимо столбов: ограда по контуру обязана быть замкнутой"
                        + " Иначе тест fence_FlatLand_ClosedAndGatesEvenlySpaced перестанет ловить эту поломку. При смене fence_FlatLand_ClosedAndGatesEvenlySpaced проверь это утверждение.");
        for (int span : FenceGates.spans(fence)) {
            assertTrue(
                    span >= FenceGateRule.MIN_SPAN - 3 && span <= FenceGateRule.MAX_SPAN,
                    "Пролёт между калитками " + span + " вне " + FenceGateRule.MIN_SPAN + ".."
                            + FenceGateRule.MAX_SPAN + " (" + FenceGates.spans(fence) + "): калитки равномерно "
                            + "по всем сторонам, иначе обход до калитки слишком долгий"
                            + " При смене fence_FlatLand_ClosedAndGatesEvenlySpaced проверь это утверждение.");
        }
    }

    @Test
    void plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled() {
        TownPlan base = plan((x, z) -> 70, (x, z) -> 0);
        TownPlan grown =
                TownPlanner.plant(grid((x, z) -> 70, (x, z) -> 0), base, RULES, STAGES, List.of("orchard_jungle"), 1);
        Placement extra = grown.placement("orchard_jungle")
                .orElseThrow(() -> new AssertionError("Сад джунглей не появился в плане: " + grown.unplaced()));
        assertTrue(
                grown.placement(StageSites.ORCHARD_EXTRA).isEmpty(),
                "Готовое место orchard_extra занято породой, а не осталось вторым садом-двойником"
                        + " Иначе тест plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled перестанет ловить эту поломку. При смене plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled проверь это утверждение.");
        for (Placement other : grown.placements()) {
            assertFalse(
                    !other.equals(extra)
                            && other.overlaps(extra, RULES.spacing().gap()),
                    "Сад джунглей пересекает '" + other.id() + "': у каждой породы своё место"
                            + " Иначе тест plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled перестанет ловить эту поломку. При смене plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled проверь это утверждение.");
        }
        StageSite site = StageSites.of(grown, 1, grown.anchor().y(), RULES);
        assertTrue(
                site.inside(extra.x(), extra.z()) && site.inside(extra.x() + extra.width() - 1, extra.z()),
                "Сад джунглей " + extra + " оказался вне ограды этапа: всё поселение внутри контура"
                        + " Иначе тест plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled перестанет ловить эту поломку. При смене plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled проверь это утверждение.");
        assertTrue(
                StageSites.insideEnclosure(site),
                "Каждый участок плана этапа обязан быть внутри ограды"
                        + " Иначе тест plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled перестанет ловить эту поломку. При смене plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled проверь это утверждение.");
        for (ColumnMask.Cell cell : extra.mask().cells()) {
            assertTrue(
                    site.level().contains(cell.x(), cell.z()) || site.mineColumn(cell.x(), cell.z()),
                    "Клетка " + cell + " сада не выравнивается: деревья повиснут или утонут"
                            + " Иначе тест plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled перестанет ловить эту поломку. При смене plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled проверь это утверждение.");
        }
        assertEquals(
                "oak",
                site.orchardWood(),
                "Главный сад — яблоневый (дуб), даже если порода поселения другая"
                        + " Иначе тест plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled перестанет ловить эту поломку. При смене plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled проверь это утверждение.");
        assertEquals(
                "jungle",
                site.extraOrchards().stream()
                        .filter(plot -> "orchard_jungle".equals(plot.building()))
                        .findFirst()
                        .orElseThrow()
                        .wood(),
                "Порода сада берётся из постройки графа orchard_jungle"
                        + " Иначе тест plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled перестанет ловить эту поломку. При смене plan_ExtraOrchardOnDemand_ApartInsideFenceAndLeveled проверь это утверждение.");
    }

    @Test
    void plan_SameSpeciesTwice_NumberedPlots() {
        TownPlan base = plan((x, z) -> 70, (x, z) -> 0);
        TownPlan grown = TownPlanner.plant(
                grid((x, z) -> 70, (x, z) -> 0),
                base,
                RULES,
                STAGES,
                List.of("orchard_jungle_2", "orchard_jungle_3"),
                1);
        Placement second = grown.placement("orchard_jungle_2")
                .orElseThrow(() -> new AssertionError("Второй сад джунглей не появился: " + grown.unplaced()));
        Placement third = grown.placement("orchard_jungle_3")
                .orElseThrow(() -> new AssertionError("Третий сад джунглей не появился: " + grown.unplaced()));
        assertEquals(
                16,
                second.width(),
                "Сад породы строится по шаблону orchard_extra шириной 16 (участок 16×16)"
                        + " Иначе тест plan_SameSpeciesTwice_NumberedPlots перестанет ловить эту поломку. При смене plan_SameSpeciesTwice_NumberedPlots проверь это утверждение.");
        assertEquals(
                16,
                third.width(),
                "Второй участок той же породы тоже берёт шаблон orchard_extra 16×16"
                        + " Иначе тест plan_SameSpeciesTwice_NumberedPlots перестанет ловить эту поломку. При смене plan_SameSpeciesTwice_NumberedPlots проверь это утверждение.");
        assertFalse(
                second.overlaps(third, 0),
                "Сады одной породы не накладываются: у каждого своё место"
                        + " Иначе тест plan_SameSpeciesTwice_NumberedPlots перестанет ловить эту поломку. При смене plan_SameSpeciesTwice_NumberedPlots проверь это утверждение.");
        StageSite site = StageSites.of(grown, 1, grown.anchor().y(), RULES);
        assertEquals(
                2,
                site.extraOrchards().size(),
                "Обе площадки одной породы видны поселению: " + site.extraOrchards()
                        + " Иначе тест plan_SameSpeciesTwice_NumberedPlots перестанет ловить эту поломку. При смене plan_SameSpeciesTwice_NumberedPlots проверь это утверждение.");
        assertTrue(
                site.extraOrchards().stream().allMatch(plot -> "jungle".equals(plot.wood())),
                "Порода обеих площадок — джунгли: номер не меняет породу"
                        + " Иначе тест plan_SameSpeciesTwice_NumberedPlots перестанет ловить эту поломку. При смене plan_SameSpeciesTwice_NumberedPlots проверь это утверждение.");
    }

    static TownPlan plan(IntBinaryOperator feet, IntBinaryOperator water) {
        return TownFixtures.plan(feet, water);
    }

    static TerrainGrid grid(IntBinaryOperator feet, IntBinaryOperator water) {
        return TownFixtures.grid(feet, water);
    }

    static boolean escapes(FenceLine fence) {
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

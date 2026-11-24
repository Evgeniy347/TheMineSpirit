package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TownMapConvertTests {
    private static final TownRules RULES = TownRules.load();
    private static final BlockCoord ANCHOR = new BlockCoord(100, 64, 100);

    @Test
    void convert_LegacyPlan_RebuildsSameStreetsAndWall() {
        TownPlan plan =
                TownMapRules.build(TownMapFixtures.small(), RULES, TownMapFixtures.REGION, ANCHOR, building -> 1);

        TownPlan back = TownMapRules.build(
                TownMapConvert.of(plan, TownMapFixtures.REGION), RULES, TownMapFixtures.REGION, ANCHOR, building -> 1);

        assertEquals(
                cells(plan.streets().main()),
                cells(back.streets().main()),
                "Экспорт старого плана в карту и обратно сохраняет главные улицы клетка в клетку: иначе "
                        + "миграция сдвигает проезды, и дома оказываются на дороге"
                        + " Иначе тест convert_LegacyPlan_RebuildsSameStreetsAndWall перестанет ловить эту поломку. При смене convert_LegacyPlan_RebuildsSameStreetsAndWall проверь это утверждение.");
        assertEquals(
                cells(plan.streets().ring()),
                cells(back.streets().ring()),
                "Кольцо после круга остаётся той же полосой: правило отступа не зависит от того, "
                        + "хранилась ли карта прямоугольником или видом ring"
                        + " Иначе тест convert_LegacyPlan_RebuildsSameStreetsAndWall перестанет ловить эту поломку. При смене convert_LegacyPlan_RebuildsSameStreetsAndWall проверь это утверждение.");
        assertEquals(
                new HashSet<>(plan.fence().posts()),
                new HashSet<>(back.fence().posts()),
                "Стена переживает экспорт тем же набором клеток: иначе кладка пойдёт по другой линии, "
                        + "чем разбирался старый забор"
                        + " Иначе тест convert_LegacyPlan_RebuildsSameStreetsAndWall перестанет ловить эту поломку. При смене convert_LegacyPlan_RebuildsSameStreetsAndWall проверь это утверждение.");
        assertEquals(
                plan.placements(),
                back.placements(),
                "Участки при миграции не меняются: id, размер и поворот те же; иначе живые постройки "
                        + "разъедутся с планом"
                        + " Иначе тест convert_LegacyPlan_RebuildsSameStreetsAndWall перестанет ловить эту поломку. При смене convert_LegacyPlan_RebuildsSameStreetsAndWall проверь это утверждение.");
    }

    @Test
    void convert_WallOutsideRegion_MovesToRegionEdge() {
        TownPlan plan =
                TownMapRules.build(TownMapFixtures.small(), RULES, TownMapFixtures.REGION, ANCHOR, building -> 1);
        ColumnMask smaller = ColumnMask.rect(20, 20, 180, 180);

        TownMap map = TownMapConvert.of(plan, smaller);

        assertTrue(
                map.wall().stream()
                        .flatMap(segment -> segmentCells(segment).stream())
                        .allMatch(cell -> smaller.contains(cell.x(), cell.z())),
                "Стена миграции лежит внутри текущей границы: иначе карту нельзя применить; получено " + map.wall()
                        + " Иначе тест convert_WallOutsideRegion_MovesToRegionEdge перестанет ловить эту поломку. При смене convert_WallOutsideRegion_MovesToRegionEdge проверь это утверждение.");
    }

    private static List<ColumnMask.Cell> segmentCells(TownMap.WallSegment segment) {
        List<ColumnMask.Cell> cells = new ArrayList<>();
        for (int x = segment.minX(); x <= segment.maxX(); x++) {
            for (int z = segment.minZ(); z <= segment.maxZ(); z++) {
                cells.add(new ColumnMask.Cell(x, z));
            }
        }
        return cells;
    }

    @Test
    void convert_RectangleWall_FourSegments() {
        List<ColumnMask.Cell> loop = List.of(
                new ColumnMask.Cell(0, 0),
                new ColumnMask.Cell(1, 0),
                new ColumnMask.Cell(2, 0),
                new ColumnMask.Cell(2, 1),
                new ColumnMask.Cell(2, 2),
                new ColumnMask.Cell(1, 2),
                new ColumnMask.Cell(0, 2),
                new ColumnMask.Cell(0, 1));

        List<TownMap.WallSegment> segments = TownMapConvert.segments(loop);

        assertEquals(
                4,
                segments.size(),
                "Прямоугольная стена схлопывается в четыре отрезка: иначе JSON пухнет сотнями клеток "
                        + "и правка вручную нечитаема; получено " + segments
                        + " Иначе тест convert_RectangleWall_FourSegments перестанет ловить эту поломку. При смене convert_RectangleWall_FourSegments проверь это утверждение.");
        assertTrue(
                segments.stream().allMatch(segment -> segment.x1() == segment.x2() || segment.z1() == segment.z2()),
                "Каждый отрезок идёт по одной оси: по ним же строится стена из схем; диагональ развалила бы кладку "
                        + "на отдельные клетки; получено " + segments
                        + " Иначе тест convert_RectangleWall_FourSegments перестанет ловить эту поломку. При смене convert_RectangleWall_FourSegments проверь это утверждение.");
    }

    private static Set<ColumnMask.Cell> cells(ColumnMask mask) {
        return new HashSet<>(mask.cells());
    }
}

package com.theminespirit.core.board.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.town.ColumnMask;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteDiggingTests {
    private static final int NO_WATER = Integer.MIN_VALUE;

    private static ColumnMask area() {
        return ColumnMask.rect(0, 0, 2, 0);
    }

    private static PlotLeveling plan(Map<Integer, Integer> tops, Map<Integer, Integer> water) {
        return RouteDigging.plan(
                area(),
                (x, z) -> tops.getOrDefault(x, tops.getOrDefault(-1, 64)),
                (x, y, z) -> water.getOrDefault(x, 0),
                NO_WATER);
    }

    @Test
    void layers_UnevenGround_AllColumnsCutToLowest() {
        Map<Integer, Integer> tops = Map.of(0, 70, 1, 73, 2, 71);
        PlotLeveling plan = plan(tops, Map.of());

        assertEquals(
                71,
                plan.cut().stream().mapToInt(PlotLeveling.Box::y1).min().orElseThrow(),
                "Нижняя граница — на блок выше низшей колонки");
        assertEquals(
                73, plan.cut().stream().mapToInt(PlotLeveling.Box::y2).max().orElseThrow(), "Верхняя — высшая");
        assertTrue(
                plan.cut().stream().allMatch(box -> box.y1() == 71),
                "Все колонки срезаются до одного уровня 70: перепад в 2 блока не остаётся, ям нет");
    }

    @Test
    void layers_WaterInsideColumn_StopsAtWaterLevel() {
        Map<Integer, Integer> tops = Map.of(0, 66, 1, 64, 2, 66);
        PlotLeveling plan = plan(tops, Map.of());

        PlotLeveling wet = RouteDigging.plan(area(), (x, z) -> tops.get(x), (x, y, z) -> 0, 63);

        assertEquals(
                65,
                wet.cut().stream().mapToInt(PlotLeveling.Box::y1).min().orElseThrow(),
                "Ниже уровня воды не копают");
        assertTrue(
                wet.cut().stream().allMatch(box -> box.y1() > 63),
                "Ни один срез не заходит на уровень воды или ниже: вода не заливает раскоп");
        assertEquals(plan.cut().size(), wet.cut().size(), "Колонки выше воды срезаются как обычно");
    }

    @Test
    void layers_TooMuchWaterAround_BlockSkipped() {
        Map<Integer, Integer> tops = Map.of(0, 68, 1, 70, 2, 68, 3, 66);
        Map<Integer, Integer> water = new HashMap<>(Map.of(1, 11));
        PlotLeveling plan = RouteDigging.plan(
                ColumnMask.rect(0, 0, 3, 0), (x, z) -> tops.get(x), (x, y, z) -> water.getOrDefault(x, 0), NO_WATER);

        assertTrue(
                plan.cut().stream().noneMatch(box -> box.x1() == 1),
                "Блок с 11 блоками воды вокруг не добывают: затопление опаснее ресурса");
        assertEquals(2, plan.cut().size(), "Соседние сухие колонки срезаются по слою, а мокрую оставляют");
    }

    @Test
    void layers_ExactlyTenWater_BlockAllowed() {
        Map<Integer, Integer> tops = Map.of(0, 66);
        Map<Integer, Integer> water = new HashMap<>(Map.of(0, 10));
        PlotLeveling plan = RouteDigging.plan(
                area(), (x, z) -> tops.getOrDefault(x, 64), (x, y, z) -> water.getOrDefault(x, 0), NO_WATER);

        assertTrue(
                plan.cut().stream().anyMatch(box -> box.x1() == 0),
                "Ровно 10 блоков воды вокруг — предел разрешён: запрет начинается с 11");
    }

    @Test
    void layers_TopDownOrder_RemovesSurfaceFirst() {
        List<PlotLeveling.Box> layers =
                RouteDigging.layers(ColumnMask.rect(0, 0, 1, 0), (x, z) -> x == 0 ? 66 : 68, (x, y, z) -> 0, NO_WATER);

        assertEquals(68, layers.get(0).y1(), "Первым снимают верхний слой: дух не подкапывает снизу");
        assertEquals(67, layers.get(layers.size() - 1).y1(), "Последним — нижний слой среза");
    }

    @Test
    void plan_AllColumnsBelowWater_Empty() {
        PlotLeveling plan = RouteDigging.plan(area(), (x, z) -> 62, (x, y, z) -> 0, 63);

        assertTrue(plan.cut().isEmpty(), "Всё дно ниже воды: добыча не начинается, чтобы не затопить участок");
    }
}

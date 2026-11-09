package com.theminespirit.core.board.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuarryPlannerTests {
    private static final BlockCoord SITE = new BlockCoord(40, 64, 20);

    @Test
    void plan_FlatDirtWithoutWater_DigsSteppedQuarry() {
        QuarryArea area = QuarryArea.at(RouteResource.DIRT, SITE);
        Probe probe = Probe.flat(64, "dirt");

        QuarryPlan plan = QuarryPlanner.plan(area, probe);

        assertEquals(
                164,
                plan.reserve(),
                "На ровном дёрне 9×9 карьер слоями даёт 164 блока: край 1, центр 4 — иначе дух копает стену, "
                        + "из которой не выйти");
        assertEquals(plan.reserve(), plan.cutVolume(), "Запас и срезка — одно и то же: иначе % остатка врёт");
        assertFalse(plan.waterLimited(), "Воды рядом нет — ограничение по воде не должно появляться");
    }

    @Test
    void plan_StepAdjacentColumns_DifferByOneLayer() {
        QuarryArea area = QuarryArea.at(RouteResource.DIRT, SITE);
        Probe probe = Probe.flat(64, "dirt");

        QuarryPlan plan = QuarryPlanner.plan(area, probe);
        Map<Integer, Integer> bottom = new HashMap<>();
        for (PlotLeveling.Box box : plan.leveling().cut()) {
            bottom.put(box.x1() * 1000 + box.z1(), box.y1());
        }

        List<String> steep = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : bottom.entrySet()) {
            int x = Math.floorDiv(entry.getKey(), 1000);
            int z = Math.floorMod(entry.getKey(), 1000);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Integer next = bottom.get((x + dx) * 1000 + (z + dz));
                    if (next != null && Math.abs(next - entry.getValue()) > 1) {
                        steep.add(x + "," + z + " → " + (x + dx) + "," + (z + dz));
                    }
                }
            }
        }
        assertEquals(
                List.of(),
                steep,
                "Соседние колонки карьера отличаются не больше чем на блок: из ямы выходят "
                        + "ступенями, а не прыжком со стены");
    }

    @Test
    void plan_WaterNearby_StopsAtWaterLevel() {
        QuarryArea area = QuarryArea.at(RouteResource.SAND, SITE);
        Probe probe = Probe.flat(64, "sand").water(62);

        QuarryPlan plan = QuarryPlanner.plan(area, probe);

        assertEquals(
                130,
                plan.reserve(),
                "Вода на 62 запрещает копать ниже: со дна на уровне воды 130 блоков песка вместо 164 — "
                        + "иначе карьер затопит и дух утонет");
        assertTrue(
                plan.waterLimited(),
                "Ограничение воды видно в отчёте плана: иначе стратег не отличит мелкий карьер "
                        + "у воды от бедного места");
    }

    @Test
    void plan_StoneUnderDirt_StopsAtStone() {
        QuarryArea area = QuarryArea.at(RouteResource.DIRT, SITE);
        Probe probe = Probe.flat(64, "dirt").block(40, 63, 20, "stone");

        QuarryPlan plan = QuarryPlanner.plan(area, probe);

        assertEquals(
                161,
                plan.reserve(),
                "Камень под дёрном останавливает колонку: добывают только подряд идущий ресурс, камень не считают "
                        + "и не ломают");
    }

    @Test
    void plan_ForbiddenBlock_SkipsWholeColumn() {
        QuarryArea area = QuarryArea.at(RouteResource.DIRT, SITE);
        Probe probe = Probe.flat(64, "dirt").prohibit(40, 64, 20);

        QuarryPlan plan = QuarryPlanner.plan(area, probe);

        assertEquals(
                160,
                plan.reserve(),
                "Блок игрока в колонке не трогают: колонка не входит в карьер, иначе дух ломает чужую постройку");
    }

    @Test
    void plan_UnloadedColumn_NotCounted() {
        QuarryArea area = QuarryArea.at(RouteResource.DIRT, SITE);
        Probe probe = Probe.flat(64, "dirt").unloaded(40, 20);

        QuarryPlan plan = QuarryPlanner.plan(area, probe);

        assertEquals(
                160,
                plan.reserve(),
                "Незагруженный чанк — «неизвестно», а не воздух: его запас не выдумывают и в план не берут");
    }

    private static final class Probe implements QuarryProbe {
        private final int top;
        private final String block;
        private int waterLevel = NO_WATER;
        private final Set<Integer> unloaded = new HashSet<>();
        private final Map<Integer, String> blocks = new HashMap<>();
        private final Set<Integer> forbidden = new HashSet<>();

        private Probe(int top, String block) {
            this.top = top;
            this.block = block;
        }

        static Probe flat(int top, String block) {
            return new Probe(top, block);
        }

        Probe water(int level) {
            waterLevel = level;
            return this;
        }

        Probe unloaded(int x, int z) {
            unloaded.add(key(x, z));
            return this;
        }

        Probe block(int x, int y, int z, String name) {
            blocks.put(key(x, y, z), name);
            return this;
        }

        Probe prohibit(int x, int y, int z) {
            forbidden.add(key(x, y, z));
            return this;
        }

        @Override
        public boolean loaded(int x, int z) {
            return !unloaded.contains(key(x, z));
        }

        @Override
        public int top(int x, int z) {
            return top;
        }

        @Override
        public String block(int x, int y, int z) {
            return blocks.getOrDefault(key(x, y, z), block);
        }

        @Override
        public int waterLevel() {
            return waterLevel;
        }

        @Override
        public int waterAround(int x, int y, int z) {
            return 0;
        }

        @Override
        public boolean forbidden(int x, int y, int z) {
            return forbidden.contains(key(x, y, z));
        }

        private static int key(int x, int y, int z) {
            return x * 31 + y * 1009 + z * 7919;
        }

        private static int key(int x, int z) {
            return x * 1000 + z;
        }
    }
}

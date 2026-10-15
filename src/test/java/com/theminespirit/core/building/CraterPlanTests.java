package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class CraterPlanTests {
    private static final BlockCoord CENTER = new BlockCoord(10, 64, -5);

    private static TestCraterSurface flatSurface(int radius, int level) {
        TestCraterSurface surface = new TestCraterSurface(CENTER, radius);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                surface.level(CENTER.x() + dx, CENTER.z() + dz, level);
            }
        }
        return surface;
    }

    @Test
    void plan_OneDeepHole_FillsCellAtHoleAndStopsAtGround() {
        TestCraterSurface surface = flatSurface(0, 64);
        surface.hole(CENTER.x(), CENTER.z(), 1);

        CraterPlan.Plan plan = CraterPlan.plan(surface);

        assertEquals(List.of(new BlockCoord(CENTER.x(), 64, CENTER.z())), plan.cells(),
                "Яма глубиной 1 обязана засыпаться одним блоком на уровне земли");
        assertEquals(1, plan.blockCount(),
                "Число блоков обязано совпасть с числом пустых клеток плана");
    }

    @Test
    void plan_DeepHole_FillsBottomUpToGroundLevel() {
        TestCraterSurface surface = flatSurface(0, 64);
        surface.hole(CENTER.x(), CENTER.z(), 4);

        CraterPlan.Plan plan = CraterPlan.plan(surface);

        assertEquals(List.of(
                        new BlockCoord(CENTER.x(), 61, CENTER.z()),
                        new BlockCoord(CENTER.x(), 62, CENTER.z()),
                        new BlockCoord(CENTER.x(), 63, CENTER.z()),
                        new BlockCoord(CENTER.x(), 64, CENTER.z())),
                plan.cells(),
                "Яма глубиной 4 обязана засыпаться четырьмя клетками снизу вверх до уровня земли");
    }

    @Test
    void plan_NoHole_ReturnsEmptyPlan() {
        TestCraterSurface surface = flatSurface(0, 64);

        CraterPlan.Plan plan = CraterPlan.plan(surface);

        assertTrue(plan.cells().isEmpty(),
                "Столбец без ямы не засыпают: над уровнем земли нет пустых клеток");
        assertEquals(0, plan.blockCount(),
                "Пустой план не тратит блоки");
    }

    @Test
    void plan_WaterOrLavaInHole_NotFilled() {
        TestCraterSurface surface = flatSurface(0, 64);
        surface.hole(CENTER.x(), CENTER.z(), 4);
        BlockCoord water = new BlockCoord(CENTER.x(), 61, CENTER.z());
        surface.solid(water);

        CraterPlan.Plan plan = CraterPlan.plan(surface);

        assertTrue(!plan.cells().contains(water),
                "Воду в яме не засыпают: жидкость остаётся, иначе дух тратит блоки и заливает источник");
        assertEquals(List.of(
                        new BlockCoord(CENTER.x(), 62, CENTER.z()),
                        new BlockCoord(CENTER.x(), 63, CENTER.z()),
                        new BlockCoord(CENTER.x(), 64, CENTER.z())),
                plan.cells(),
                "Над водой засыпают только воздух до уровня земли");
    }

    @Test
    void plan_ProtectedCellInHole_Skipped() {
        TestCraterSurface surface = flatSurface(0, 64);
        surface.hole(CENTER.x(), CENTER.z(), 3);
        BlockCoord protectedCell = new BlockCoord(CENTER.x(), 63, CENTER.z());
        surface.protect(protectedCell);

        CraterPlan.Plan plan = CraterPlan.plan(surface);

        assertTrue(!plan.cells().contains(protectedCell),
                "Защищённую (чужую) клетку не засыпают: политика защиты блоков выше ремонта");
        assertEquals(List.of(
                        new BlockCoord(CENTER.x(), 62, CENTER.z()),
                        new BlockCoord(CENTER.x(), 64, CENTER.z())),
                plan.cells(),
                "Незащищённые пустоты вокруг защищённой клетки засыпают, а саму защищённую пропускают");
    }

    @Test
    void plan_MultipleColumns_OnlyHolesInsideRadius() {
        TestCraterSurface surface = flatSurface(1, 64);
        surface.hole(CENTER.x(), CENTER.z(), 1);
        surface.hole(CENTER.x() + 1, CENTER.z(), 2);

        CraterPlan.Plan plan = CraterPlan.plan(surface);

        assertEquals(3, plan.blockCount(),
                "Две ямы глубиной 1 и 2 внутри диска радиуса 1 дают 3 клетки; клетки вне диска не берутся");
    }
}
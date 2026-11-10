package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class SurfaceGoalTests {
    private static final BlockCoord HOME = new BlockCoord(-175182, -47, -647977);

    @Test
    void home_CellAtHome_IsGoal() {
        assertTrue(SurfaceGoal.home(HOME, HOME, 2), "Клетка дома на поверхности — цель маршрута выхода");
    }

    @Test
    void home_AnySkyFarAway_IsNotGoal() {
        assertFalse(
                SurfaceGoal.home(new BlockCoord(-175190, -47, -647982), HOME, 2),
                "Любое небо — не цель: маршрут уводил духа на край площадки и он падал в пустоту "
                        + "(падающий шаг DigOutGameTests после выхода из кармана)");
    }

    @Test
    void home_EdgeOfRadius_IsGoal() {
        assertTrue(
                SurfaceGoal.home(new BlockCoord(HOME.x() + 2, HOME.y() + 1, HOME.z() - 2), HOME, 2),
                "Клетка на краю радиуса дома — ещё цель: тот же радиус, что у положительного исхода");
    }

    @Test
    void home_OneOutsideRadius_IsNotGoal() {
        assertFalse(
                SurfaceGoal.home(new BlockCoord(HOME.x() + 3, HOME.y(), HOME.z()), HOME, 2),
                "За радиусом дома небо не считается выходом");
    }
}

package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.api.PathReuse;
import org.junit.jupiter.api.Test;

class PathReuseTests {
    @Test
    void keep_SameTargetWithActivePath_IsTrue() {
        assertTrue(
                PathReuse.keep(true, -156, 71, -489, -156, 71, -489, true),
                "Повторный запрос пути к той же цели при живом пути обязан переиспользовать путь: иначе дух "
                        + "разворачивается к началу нового пути и дёргается на месте");
    }

    @Test
    void keep_SameTargetWithoutActivePath_IsFalse() {
        assertFalse(
                PathReuse.keep(true, -156, 71, -489, -156, 71, -489, false),
                "Закончившийся путь не переиспользуют: к той же цели нужен новый путь");
    }

    @Test
    void keep_OtherTarget_IsFalse() {
        assertFalse(
                PathReuse.keep(true, -156, 71, -489, -178, 73, -518, true),
                "Для другой цели путь строится заново: иначе дух пойдёт к старой точке");
    }

    @Test
    void keep_NoPreviousTarget_IsFalse() {
        assertFalse(PathReuse.keep(false, 0, 0, 0, -156, 71, -489, true), "Без прошлой цели переиспользовать нечего");
    }

    @Test
    void keep_ApproachSameCellEveryTick_KeepsPath() {
        boolean first = PathReuse.keep(false, 0, 0, 0, -163, 75, -488, false);
        boolean repeated = PathReuse.keep(true, -163, 75, -488, -163, 75, -488, true);

        assertFalse(
                first,
                "Первый запрос к новой цели строит свежий путь: иначе кеш прошлой цели увёл бы духа к "
                        + "чужой точке, и подход к новой цели пропал бы. При смене keep проверь "
                        + "PathReuseTests.keep_OtherTarget_IsFalse и живой goto к меняющимся точкам доски");
        assertTrue(
                repeated,
                "Approach зовёт путь к той же клетке каждый тик, в том числе перегрузкой с floorLevel: без "
                        + "переиспользования путь пересоздаётся, next и yaw мигают через тик (окно 7, spirit-1), "
                        + "иначе дух дрожит на месте вместо хода. При смене moveTo в SpiritNavigation проверь "
                        + "обе перегрузки: четырёхаргументную и пятиаргументную с floorLevel");
    }
}

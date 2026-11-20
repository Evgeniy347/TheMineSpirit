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
}

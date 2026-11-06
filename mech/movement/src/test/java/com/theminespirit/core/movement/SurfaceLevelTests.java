package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SurfaceLevelTests {
    @Test
    void pit_TenDeep_NotEscapedEvenUnderOpenSky() {
        assertFalse(
                SurfaceLevel.escaped(true, 53, List.of(63, 63, 63, 63)),
                "Дух на дне открытой ямы 10 блоков не «на поверхности»: небо над ним не выход (observe24 B1)");
    }

    @Test
    void pit_TwoDeep_Escaped() {
        assertTrue(
                SurfaceLevel.escaped(true, 61, List.of(63, 63, 63, 63)),
                "Яма глубиной 2 не ловушка: дух выходит шагом, метка не нужна");
    }

    @Test
    void flatGround_LevelAround_Escaped() {
        assertTrue(
                SurfaceLevel.escaped(true, 63, List.of(63, 63, 64, 62)),
                "Ровная поверхность с бугром в блок — не ловушка");
    }

    @Test
    void underRoof_NoSky_NotEscaped() {
        assertFalse(
                SurfaceLevel.escaped(false, 63, List.of(63, 63, 63, 63)),
                "Под крышей неба нет: выход на поверхность не подтверждён, метка держится");
    }

    @Test
    void cliffBesideSpirit_HighGroundAround_NotEscaped() {
        assertFalse(
                SurfaceLevel.escaped(true, 63, List.of(70, 70, 70, 70)),
                "У подножия обрыва дух не на уровне окружающей поверхности: спасение ещё нужно");
    }
}

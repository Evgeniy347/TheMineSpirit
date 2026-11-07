package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SurfaceLevelTests {
    @Test
    void pit_TenDeep_NotEscapedEvenUnderOpenSky() {
        assertFalse(
                SurfaceLevel.escaped(true, 53, List.of(63, 63, 63, 63), false),
                "Дух на дне открытой ямы 10 блоков не «на поверхности»: небо над ним не выход (observe24 B1)");
    }

    @Test
    void pit_TwoDeep_Escaped() {
        assertTrue(
                SurfaceLevel.escaped(true, 61, List.of(63, 63, 63, 63), false),
                "Яма глубиной 2 не ловушка: дух выходит шагом, метка не нужна");
    }

    @Test
    void flatGround_LevelAround_Escaped() {
        assertTrue(
                SurfaceLevel.escaped(true, 63, List.of(63, 63, 64, 62), false),
                "Ровная поверхность с бугром в блок — не ловушка");
    }

    @Test
    void underRoof_NoSkyNoWalkExit_NotEscaped() {
        assertFalse(
                SurfaceLevel.escaped(false, 63, List.of(63, 63, 63, 63), false),
                "Под крышей без пешего выхода на поверхность метка держится: небо над духом не подтверждает выход");
    }

    @Test
    void underRoof_WalkExit_Escaped() {
        assertTrue(
                SurfaceLevel.escaped(false, 63, List.of(63, 63, 63, 63), true),
                "Пеший выход на открытое небо — выход, даже если сам дух стоит под навесом: иначе он зря "
                        + "запускает самоспасение у подножия обрыва (debtnav, п.1)");
    }

    @Test
    void cliffFoot_WalkExitAroundCliff_Escaped() {
        assertTrue(
                SurfaceLevel.escaped(true, 63, List.of(70, 70, 70, 70), true),
                "У подножия обрыва дух обходит его пешком: максимум высот в радиусе — не признак ямы "
                        + "(debtnav, п.1)");
    }

    @Test
    void pit_NoWalkExit_NotEscaped() {
        assertFalse(
                SurfaceLevel.escaped(true, 63, List.of(70, 70, 70, 70), false),
                "Замкнутая яма без пешего выхода — ловушка: метка держится, спасение нужно");
    }
}

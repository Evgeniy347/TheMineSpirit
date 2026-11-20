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
                "Дух на дне открытой ямы 10 блоков не «на поверхности»: небо над ним не выход (observe24 B1)"
                        + " Иначе тест pit_TenDeep_NotEscapedEvenUnderOpenSky перестанет ловить эту поломку. При смене pit_TenDeep_NotEscapedEvenUnderOpenSky проверь это утверждение.");
    }

    @Test
    void pit_TwoDeep_Escaped() {
        assertTrue(
                SurfaceLevel.escaped(true, 61, List.of(63, 63, 63, 63), false),
                "Яма глубиной 2 не ловушка: дух выходит шагом, метка не нужна"
                        + " Иначе тест pit_TwoDeep_Escaped перестанет ловить эту поломку. При смене pit_TwoDeep_Escaped проверь это утверждение.");
    }

    @Test
    void flatGround_LevelAround_Escaped() {
        assertTrue(
                SurfaceLevel.escaped(true, 63, List.of(63, 63, 64, 62), false),
                "Ровная поверхность с бугром в блок — не ловушка"
                        + " Иначе тест flatGround_LevelAround_Escaped перестанет ловить эту поломку. При смене flatGround_LevelAround_Escaped проверь это утверждение.");
    }

    @Test
    void underRoof_NoSkyNoWalkExit_NotEscaped() {
        assertFalse(
                SurfaceLevel.escaped(false, 63, List.of(63, 63, 63, 63), false),
                "Под крышей без пешего выхода на поверхность метка держится: небо над духом не подтверждает выход"
                        + " Иначе тест underRoof_NoSkyNoWalkExit_NotEscaped перестанет ловить эту поломку. При смене underRoof_NoSkyNoWalkExit_NotEscaped проверь это утверждение.");
    }

    @Test
    void underRoof_WalkExit_Escaped() {
        assertTrue(
                SurfaceLevel.escaped(false, 63, List.of(63, 63, 63, 63), true),
                "Пеший выход на открытое небо — выход, даже если сам дух стоит под навесом: иначе он зря "
                        + "запускает самоспасение у подножия обрыва (debtnav, п.1)"
                        + " При смене underRoof_WalkExit_Escaped проверь это утверждение.");
    }

    @Test
    void cliffFoot_WalkExitAroundCliff_Escaped() {
        assertTrue(
                SurfaceLevel.escaped(true, 63, List.of(70, 70, 70, 70), true),
                "У подножия обрыва дух обходит его пешком: максимум высот в радиусе — не признак ямы "
                        + "(debtnav, п.1)"
                        + " Иначе тест cliffFoot_WalkExitAroundCliff_Escaped перестанет ловить эту поломку. При смене cliffFoot_WalkExitAroundCliff_Escaped проверь это утверждение.");
    }

    @Test
    void pit_NoWalkExit_NotEscaped() {
        assertFalse(
                SurfaceLevel.escaped(true, 63, List.of(70, 70, 70, 70), false),
                "Замкнутая яма без пешего выхода — ловушка: метка держится, спасение нужно"
                        + " Иначе тест pit_NoWalkExit_NotEscaped перестанет ловить эту поломку. При смене pit_NoWalkExit_NotEscaped проверь это утверждение.");
    }
}

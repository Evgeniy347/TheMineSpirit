package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.api.CatalogLimits;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CraterTests {
    private static final BlockCoord CENTER = new BlockCoord(10, 64, -5);

    private static Crater crater(int radius, int level) {
        return new Crater(UUID.randomUUID(), CENTER, radius, level, 0L);
    }

    @Test
    void covers_PointInsideDisk_True() {
        Crater crater = crater(3, 64);

        assertTrue(
                crater.covers(CENTER),
                "Центр воронки обязан накрываться своей воронкой"
                        + " Иначе тест covers_PointInsideDisk_True перестанет ловить эту поломку. При смене covers_PointInsideDisk_True проверь это утверждение.");
        assertTrue(
                crater.covers(new BlockCoord(CENTER.x() + 3, CENTER.y(), CENTER.z())),
                "Точка на радиусе по оси обязана накрываться: диск включает границу"
                        + " Иначе тест covers_PointInsideDisk_True перестанет ловить эту поломку. При смене covers_PointInsideDisk_True проверь это утверждение.");
    }

    @Test
    void covers_PointOutsideDisk_False() {
        Crater crater = crater(3, 64);

        assertFalse(
                crater.covers(new BlockCoord(CENTER.x() + 4, CENTER.y(), CENTER.z())),
                "Точка в 4 блоках при радиусе 3 вне воронки: засыпать её нельзя"
                        + " Иначе тест covers_PointOutsideDisk_False перестанет ловить эту поломку. При смене covers_PointOutsideDisk_False проверь это утверждение.");
        assertFalse(
                crater.covers(new BlockCoord(CENTER.x() + 3, CENTER.y(), CENTER.z() + 3)),
                "Угол (3,3) дальше радиуса 3: диск не квадрат"
                        + " Иначе тест covers_PointOutsideDisk_False перестанет ловить эту поломку. При смене covers_PointOutsideDisk_False проверь это утверждение.");
    }

    @Test
    void crater_RadiusBeyondLimit_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> crater(Crater.RADIUS_MAX + 1, 64),
                "Радиус воронки выше предела обязан отвергаться: иначе план засыпки разрастается без границ"
                        + " При смене crater_RadiusBeyondLimit_Rejected проверь это утверждение.");
    }

    @Test
    void crater_LevelOutsideWorld_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> crater(3, CatalogLimits.WORLD_Y_MAX + 1),
                "Уровень земли выше мира обязан отвергаться: по нему нечего засыпать"
                        + " Иначе тест crater_LevelOutsideWorld_Rejected перестанет ловить эту поломку. При смене crater_LevelOutsideWorld_Rejected проверь это утверждение.");
    }
}

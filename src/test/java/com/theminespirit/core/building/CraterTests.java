package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
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

        assertTrue(crater.covers(CENTER), "Центр воронки обязан накрываться своей воронкой");
        assertTrue(crater.covers(new BlockCoord(CENTER.x() + 3, CENTER.y(), CENTER.z())),
                "Точка на радиусе по оси обязана накрываться: диск включает границу");
    }

    @Test
    void covers_PointOutsideDisk_False() {
        Crater crater = crater(3, 64);

        assertFalse(crater.covers(new BlockCoord(CENTER.x() + 4, CENTER.y(), CENTER.z())),
                "Точка в 4 блоках при радиусе 3 вне воронки: засыпать её нельзя");
        assertFalse(crater.covers(new BlockCoord(CENTER.x() + 3, CENTER.y(), CENTER.z() + 3)),
                "Угол (3,3) дальше радиуса 3: диск не квадрат");
    }

    @Test
    void crater_RadiusBeyondLimit_Rejected() {
        assertThrows(IllegalArgumentException.class, () -> crater(Crater.RADIUS_MAX + 1, 64),
                "Радиус воронки выше предела обязан отвергаться: иначе план засыпки разрастается без границ");
    }

    @Test
    void crater_LevelOutsideWorld_Rejected() {
        assertThrows(IllegalArgumentException.class, () -> crater(3, CatalogLimits.WORLD_Y_MAX + 1),
                "Уровень земли выше мира обязан отвергаться: по нему нечего засыпать");
    }
}
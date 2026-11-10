package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PerchLevelTests {
    private static List<Integer> around(int height) {
        return new ArrayList<>(Collections.nCopies(48, height));
    }

    @Test
    void ownPillarThirty_OpenSky_NotEscaped() {
        List<Integer> heights = around(68);
        heights.set(0, 69);
        heights.set(7, 70);

        assertTrue(
                SurfaceLevel.escaped(true, 98, heights, false),
                "Старое правило засчитывало вершину столба выходом: «выше всех под открытым небом»");
        assertFalse(
                PerchLevel.escaped(true, 98, heights, false),
                "spirit-4 10.11 на своём столбе y=98 над землёй y=68: метка «застрял» обязана остаться, "
                        + "иначе спасение со спуском не запускается и дух стоит наверху часами");
    }

    @Test
    void pillar_NextToForeignPillarSameHeight_StillPerched() {
        List<Integer> heights = around(68);
        heights.set(3, 98);
        heights.set(4, 98);

        assertTrue(
                PerchLevel.perched(true, 98, heights),
                "Соседний столб той же высоты — не земля: вокруг всё равно обрыв, дух на вершине");
    }

    @Test
    void pillar_EveryHeightAboveSafeDrop_Perched() {
        for (int height = PerchLevel.SAFE_DROP + 1; height <= 40; height++) {
            assertTrue(
                    PerchLevel.perched(true, 64 + height, around(64)),
                    "Столб высотой " + height + " выше безопасного прыжка — вершина, а не поверхность");
        }
    }

    @Test
    void step_WithinSafeDrop_NotPerched() {
        assertFalse(
                PerchLevel.perched(true, 64 + PerchLevel.SAFE_DROP, around(64)),
                "Уступ в безопасный прыжок — дух спрыгивает сам, это не застревание");
    }

    @Test
    void flatGround_Level_Escaped() {
        assertTrue(PerchLevel.escaped(true, 64, around(64), false), "Ровная земля — выход");
    }

    @Test
    void roof_LevelAround_NotPerched() {
        List<Integer> heights = around(74);
        for (int i = 0; i < 20; i++) {
            heights.set(i, 64);
        }

        assertFalse(
                PerchLevel.perched(true, 74, heights),
                "Крыша шире столба: большая часть клеток вокруг на уровне ног — решает прежнее правило");
    }

    @Test
    void pit_UnderOpenSky_StillNotEscaped() {
        assertFalse(
                PerchLevel.escaped(true, 53, around(63), false),
                "Яма по-прежнему ловушка: новое правило только добавляет вершину столба");
    }

    @Test
    void underRoof_NoSky_NotPerched() {
        assertFalse(PerchLevel.perched(false, 98, around(68)), "Без открытого неба это не вершина столба");
    }
}

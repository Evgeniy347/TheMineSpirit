package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClearArrivalTests {
    private static final ClearAnchor.Stand STAND = new ClearAnchor.Stand(10, 66, 10);

    @Test
    void reached_SameOrNeighbourCell_IsArrival() {
        assertTrue(
                ClearArrival.reached(STAND, 10, 66, 10),
                "Своя клетка якоря — прибытие: иначе подход перезапускается, а якорь уходит в опробованные, "
                        + "и расчистка объявляет клетки недостижимыми. При смене reached_SameOrNeighbourCell_IsArrival "
                        + "проверь точное совпадение клетки");
        assertTrue(
                ClearArrival.reached(STAND, 11, 66, 9),
                "Соседняя клетка в радиусе 1 — тоже прибытие: дух останавливается в 0.6 от центра и часто встаёт "
                        + "рядом; строгое равенство валило подход и клетка уходила в недостижимые (живой лог 22.11), иначе билет падает вхолостую. При смене "
                        + "reached_SameOrNeighbourCell_IsArrival проверь допуск ±1");
    }

    @Test
    void reached_OtherLevelOrTwoCellsAway_IsNotArrival() {
        assertFalse(
                ClearArrival.reached(STAND, 10, 67, 10),
                "Другой уровень — не прибытие: иначе дух считает подход завершённым на чужой высоте и якорь "
                        + "сразу пересчитывается. При смене reached_OtherLevelOrTwoCellsAway_IsNotArrival проверь "
                        + "сравнение y");
        assertFalse(
                ClearArrival.reached(STAND, 12, 66, 10),
                "Две клетки в сторону — не прибытие: иначе дух бросает подход, не дойдя до точки охвата, "
                        + "и снова пробует тот же якорь. При смене reached_OtherLevelOrTwoCellsAway_IsNotArrival "
                        + "проверь радиус 1");
    }
}

package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class WaterCellsTests {
    private static final int LIMIT = 20_000;

    @Test
    void maxCells_Constant_IsTwentyThousand() {
        assertEquals(LIMIT, WaterCells.MAX_CELLS,
                "Предел воды 20 000 клеток задан в задаче 21 п. 10: он защищает тик от разбора озера; "
                        + "меняя число осознанно, поправь и ожидание здесь");
    }

    @Test
    void add_ExactlyLimitDistinctCells_IsNotExceeded() {
        WaterCells water = new WaterCells();
        for (int i = 0; i < LIMIT; i++) {
            water.add(new BlockCoord(i, 0, 0));
        }

        assertFalse(water.exceeded(), "Ровно 20 000 клеток ещё допустимы: отказ раньше предела режет годные озёра");
        assertEquals(LIMIT, water.cells().size(), "Все 20 000 клеток обязаны остаться во множестве воды");
    }

    @Test
    void add_OneMoreThanLimit_IsExceeded() {
        WaterCells water = new WaterCells();
        for (int i = 0; i <= LIMIT; i++) {
            water.add(new BlockCoord(i, 0, 0));
        }

        assertTrue(water.exceeded(), "20 001 клетка воды обязана дать отказ: иначе предел не защищает тик");
    }

    @Test
    void add_SameCellManyTimes_CountsOnce() {
        WaterCells water = new WaterCells();
        for (int i = 0; i < 2 * LIMIT; i++) {
            water.add(new BlockCoord(1, 2, 3));
        }

        assertFalse(water.exceeded(), "Повторы одной клетки не растят множество: предел считает разные клетки");
        assertEquals(1, water.cells().size(), "Одна клетка воды — одна запись во множестве");
    }

    @Test
    void cells_AfterExceeded_Throws() {
        WaterCells water = new WaterCells();
        for (int i = 0; i <= LIMIT; i++) {
            water.add(new BlockCoord(i, 0, 0));
        }

        assertThrows(IllegalStateException.class, water::cells,
                "Неполное множество воды дало бы тихий ложный «сухо»: после превышения чтение обязано падать");
    }

    @Test
    void refusal_Text_NamesRadiusAdvice() {
        assertTrue(WaterCells.REFUSAL.contains("воды слишком много") && WaterCells.REFUSAL.contains("radius"),
                "Отказ обязан сказать стратегу, что именно уменьшать: параметр radius");
    }
}

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
        assertEquals(
                LIMIT,
                WaterCells.MAX_CELLS,
                "Предел воды 20 000 клеток задан в задаче 21 п. 10: он защищает тик от разбора озера, иначе "
                        + "ферма встаёт на одном озере и остальные работы ждут. При смене WaterCells.MAX_CELLS "
                        + "проверь add_ExactlyLimitDistinctCells и add_OneMoreThanLimit");
    }

    @Test
    void add_ExactlyLimitDistinctCells_IsNotExceeded() {
        WaterCells water = new WaterCells();
        for (int i = 0; i < LIMIT; i++) {
            water.add(new BlockCoord(i, 0, 0));
        }

        assertFalse(
                water.exceeded(),
                "Ровно 20 000 клеток ещё допустимы: отказ раньше предела режет годные озёра, иначе ферма "
                        + "остаётся без полива. При смене проверки exceed в WaterCells.add проверь соседний тест "
                        + "с лишней клеткой");
        assertEquals(
                LIMIT,
                water.cells().size(),
                "Все 20 000 клеток обязаны остаться во множестве воды: иначе часть озера теряется и полив идёт "
                        + "по неполной карте. При смене WaterCells.cells проверь cells_AfterExceeded_Throws");
    }

    @Test
    void add_OneMoreThanLimit_IsExceeded() {
        WaterCells water = new WaterCells();
        for (int i = 0; i <= LIMIT; i++) {
            water.add(new BlockCoord(i, 0, 0));
        }

        assertTrue(
                water.exceeded(),
                "20 001 клетка воды обязана дать отказ: иначе предел не защищает тик и ферма встаёт на огромном "
                        + "озере. При смене проверки в WaterCells.add проверь границу LIMIT и LIMIT+1");
    }

    @Test
    void add_SameCellManyTimes_CountsOnce() {
        WaterCells water = new WaterCells();
        for (int i = 0; i < 2 * LIMIT; i++) {
            water.add(new BlockCoord(1, 2, 3));
        }

        assertFalse(
                water.exceeded(),
                "Повторы одной клетки не растят множество: предел считает разные клетки, иначе озеро из одной "
                        + "клетки ложно переполнит счётчик. При смене WaterCells.add проверь размер cells() в "
                        + "этом тесте");
        assertEquals(
                1,
                water.cells().size(),
                "Одна клетка воды — одна запись во множестве: иначе повторные чтения раздувают карту и предел "
                        + "срабатывает раньше времени. При смене WaterCells.add проверь соседний тест с повторами");
    }

    @Test
    void cells_AfterExceeded_Throws() {
        WaterCells water = new WaterCells();
        for (int i = 0; i <= LIMIT; i++) {
            water.add(new BlockCoord(i, 0, 0));
        }

        assertThrows(
                IllegalStateException.class,
                water::cells,
                "Неполное множество воды дало бы тихий ложный «сухо»: после превышения чтение обязано падать, "
                        + "иначе дух копает ямы без воды. При смене WaterCells.cells проверь "
                        + "add_OneMoreThanLimit_IsExceeded");
    }

    @Test
    void refusal_Text_NamesRadiusAdvice() {
        assertTrue(
                WaterCells.REFUSAL.contains("воды слишком много") && WaterCells.REFUSAL.contains("radius"),
                "Отказ обязан сказать стратегу, что именно уменьшать — параметр radius, иначе он снова пришлёт "
                        + "тот же запрос и цикл встанет. При смене WaterCells.REFUSAL проверь строку отказа в "
                        + "логе фермы и отчёт стратегу");
    }
}

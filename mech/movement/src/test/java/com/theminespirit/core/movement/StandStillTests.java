package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class StandStillTests {
    private static BlockCoord cell(int y) {
        return new BlockCoord(0, y, 0);
    }

    @Test
    void stuck_SameCellUntilLimit_IsFalse() {
        StandStill still = new StandStill();
        for (int i = 1; i < StandStill.LIMIT_TICKS; i++) {
            assertFalse(
                    still.stuck(cell(70)),
                    "Первые 19 тиков в одной клетке — ещё не застревание: иначе обычная остановка духа "
                            + "(открывается проход, ждёт команды) обрывала бы шаг раньше срока. При смене "
                            + "порога проверь ожидание прохода: оно не должно считаться стоянием");
        }
    }

    @Test
    void stuck_SameCellAtLimit_IsTrue() {
        StandStill still = new StandStill();
        for (int i = 1; i < StandStill.LIMIT_TICKS; i++) {
            still.stuck(cell(70));
        }
        assertTrue(
                still.stuck(cell(70)),
                "Дух в одной клетке ровно 20 тиков — застрял: иначе шаг RouteWalk держит его 60 тиков, "
                        + "кружит у перекрытого спуска и даёт эпизод окна 49 (spirit-6 -178,30,69,-516.50, "
                        + "дистанция 0.80 не менялась). При смене порога проверь обычный шаг: 2–6 тиков");
    }

    @Test
    void stuck_MovesEachTick_IsFalse() {
        StandStill still = new StandStill();
        for (int i = 0; i < StandStill.LIMIT_TICKS * 2; i++) {
            assertFalse(
                    still.stuck(cell(70 + i)),
                    "Смена клетки каждый тик сбрасывает счётчик: иначе любой маршрут по прямой обрывался бы "
                            + "на 20-м тике. При смене правила проверь долгий переход: он не должен "
                            + "считаться застреванием");
        }
    }

    @Test
    void stuck_MoveThenOneTick_IsFalse() {
        StandStill still = new StandStill();
        for (int i = 1; i < StandStill.LIMIT_TICKS; i++) {
            still.stuck(cell(70));
        }
        still.stuck(cell(71));
        assertFalse(
                still.stuck(cell(71)),
                "После шага в соседнюю клетку отсчёт начинается заново: иначе дух, один раз постоявший, "
                        + "обрывал бы следующий шаг уже на первом тике. При смене правила проверь остановку "
                        + "на новой клетке: первые 19 тиков — не застревание");
    }

    @Test
    void stuck_AfterReset_IsFalse() {
        StandStill still = new StandStill();
        for (int i = 1; i <= StandStill.LIMIT_TICKS; i++) {
            still.stuck(cell(70));
        }
        still.reset();
        assertFalse(
                still.stuck(cell(70)),
                "reset() забывает прошлую клетку: иначе после открытия прохода сразу срабатывал бы старый "
                        + "счётчик и шаг обрывался бы без движения. При смене правила проверь выход из "
                        + "ожидания: отсчёт начинается с нуля");
    }
}

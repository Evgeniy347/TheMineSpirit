package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActivityGraceTests {
    @Test
    void keep_WithinGrace_KeepsCard() {
        assertTrue(
                ActivityGrace.keep(100, 100),
                "Карточка занятия между попытками не снимается: иначе доска каждые 100 тиков пишет "
                        + "CANCELLED/ASSIGNED на одного духа и владелец видит карусель");
        assertTrue(ActivityGrace.keep(100, 339), "Последний тик grace ещё держит карточку");
    }

    @Test
    void keep_GraceExpired_DropsCard() {
        assertFalse(ActivityGrace.keep(100, 340), "Через 240 тиков занятие считается законченным и снимается");
        assertFalse(ActivityGrace.keep(100, 500), "Долгая пауза не оставляет фантомную карточку навсегда");
    }

    @Test
    void keep_NoIdleMark_DoesNotKeep() {
        assertFalse(ActivityGrace.keep(0, 0), "Нулевая отметка простоя — карточку ещё не запускали");
        assertFalse(ActivityGrace.keep(200, 100), "Часы назад (загрузка мира) не удерживают карточку");
    }
}

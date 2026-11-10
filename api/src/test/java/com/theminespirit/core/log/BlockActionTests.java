package com.theminespirit.core.log;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BlockActionTests {
    @Test
    void refusal_WritesRefuse_NotAttemptedPlacement() {
        assertEquals(
                "refuse",
                BlockAction.of(LogCause.REFUSE, "place"),
                "Отказ в постановке обязан быть action=refuse: иначе журнал показывает place air → air и "
                        + "читатель ищет постановку, которой не было");
        assertEquals(
                "refuse",
                BlockAction.of(LogCause.REFUSE, "DEMOLISH"),
                "Отказ в ломке тоже refuse: намерение не выдаёт себя за сломанный блок");
    }

    @Test
    void realChange_AnyCause_KeepsItsAction() {
        assertEquals("place", BlockAction.of(LogCause.PLACE, "place"), "Настоящая постановка остаётся place");
        assertEquals("break", BlockAction.of(LogCause.BREAK, "break"), "Настоящая ломка остаётся break");
        assertEquals("restore", BlockAction.of(LogCause.RESTORE, "restore"), "Возврат блока остаётся restore");
    }
}

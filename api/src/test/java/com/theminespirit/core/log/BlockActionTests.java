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
                        + "читатель ищет постановку, которой не было"
                        + " При смене refusal_WritesRefuse_NotAttemptedPlacement проверь это утверждение.");
        assertEquals(
                "refuse",
                BlockAction.of(LogCause.REFUSE, "DEMOLISH"),
                "Отказ в ломке тоже refuse: намерение не выдаёт себя за сломанный блок"
                        + " Иначе тест refusal_WritesRefuse_NotAttemptedPlacement перестанет ловить эту поломку. При смене refusal_WritesRefuse_NotAttemptedPlacement проверь это утверждение.");
    }

    @Test
    void realChange_AnyCause_KeepsItsAction() {
        assertEquals(
                "place",
                BlockAction.of(LogCause.PLACE, "place"),
                "Настоящая постановка остаётся place"
                        + " Иначе тест realChange_AnyCause_KeepsItsAction перестанет ловить эту поломку. При смене realChange_AnyCause_KeepsItsAction проверь это утверждение.");
        assertEquals(
                "break",
                BlockAction.of(LogCause.BREAK, "break"),
                "Настоящая ломка остаётся break"
                        + " Иначе тест realChange_AnyCause_KeepsItsAction перестанет ловить эту поломку. При смене realChange_AnyCause_KeepsItsAction проверь это утверждение.");
        assertEquals(
                "restore",
                BlockAction.of(LogCause.RESTORE, "restore"),
                "Возврат блока остаётся restore"
                        + " Иначе тест realChange_AnyCause_KeepsItsAction перестанет ловить эту поломку. При смене realChange_AnyCause_KeepsItsAction проверь это утверждение.");
    }
}

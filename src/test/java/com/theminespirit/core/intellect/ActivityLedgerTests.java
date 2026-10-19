package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityLedgerTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void assign_NewSpirit_IsReadable() {
        ActivityLedger ledger = new ActivityLedger();
        ledger.assign(SPIRIT, ActivityId.CHOP, 100, 3.0);

        assertEquals(
                Optional.of(new ActivityAssignment(SPIRIT, ActivityId.CHOP, 100, 3.0)),
                ledger.of(SPIRIT),
                "Назначение читается: иначе вытеснение не узнает текущее занятие духа");
    }

    @Test
    void assign_SameActivity_KeepsSince() {
        ActivityLedger ledger = new ActivityLedger();
        ledger.assign(SPIRIT, ActivityId.FARM, 100, 5.0);
        ledger.assign(SPIRIT, ActivityId.FARM, 200, 6.0);

        assertEquals(
                100,
                ledger.of(SPIRIT).orElseThrow().since(),
                "Повтор той же деятельности не сдвигает since: иначе гистерезис решит, что занятие новое");
        assertEquals(
                6.0,
                ledger.of(SPIRIT).orElseThrow().demand(),
                "Спрос обновляется: иначе вытеснение сравнит устаревшее число");
    }

    @Test
    void assign_OtherActivity_ResetsSince() {
        ActivityLedger ledger = new ActivityLedger();
        ledger.assign(SPIRIT, ActivityId.FARM, 100, 5.0);
        ledger.assign(SPIRIT, ActivityId.CHOP, 200, 3.0);

        assertEquals(
                new ActivityAssignment(SPIRIT, ActivityId.CHOP, 200, 3.0),
                ledger.of(SPIRIT).orElseThrow(),
                "Смена деятельности сдвигает since: иначе новое занятие удержит чужой гистерезис");
    }

    @Test
    void clear_KnownSpirit_RemovesEntry() {
        ActivityLedger ledger = new ActivityLedger();
        ledger.assign(SPIRIT, ActivityId.CHOP, 100, 3.0);

        assertTrue(ledger.clear(SPIRIT), "Снятие известного духа подтверждает: иначе молчаливая потеря занятия");
        assertEquals(
                Optional.empty(),
                ledger.of(SPIRIT),
                "После снятия записи нет: иначе выбывший дух удержит деятельность");
        assertFalse(ledger.clear(SPIRIT), "Повторное снятие честно говорит об отсутствии: иначе двойной учёт");
    }

    @Test
    void assign_BadInputs_Throw() {
        ActivityLedger ledger = new ActivityLedger();

        assertThrows(
                IllegalArgumentException.class,
                () -> ledger.assign(null, ActivityId.CHOP, 100, 3.0),
                "Занятие без духа не назначается: план раздают конкретным духам");
        assertThrows(
                IllegalArgumentException.class,
                () -> ledger.assign(SPIRIT, ActivityId.CHOP, -1, 3.0),
                "Назначение в отрицательном тике ломает гистерезис: окно смены посчитается неверно");
        assertThrows(
                IllegalArgumentException.class,
                () -> ledger.assign(SPIRIT, ActivityId.CHOP, 100, -1),
                "Спрос вне шкалы ломает вытеснение: сравнение занятий станет несравнимым");
    }

    @Test
    void all_TwoSpirits_SortedById() {
        UUID second = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ActivityLedger ledger = new ActivityLedger();
        ledger.assign(second, ActivityId.CHOP, 100, 3.0);
        ledger.assign(SPIRIT, ActivityId.FARM, 100, 5.0);

        assertEquals(
                List.of(
                        new ActivityAssignment(SPIRIT, ActivityId.FARM, 100, 5.0),
                        new ActivityAssignment(second, ActivityId.CHOP, 100, 3.0)),
                ledger.all(),
                "Обход упорядочен по id духа: иначе план зависит от порядка назначений");
    }
}

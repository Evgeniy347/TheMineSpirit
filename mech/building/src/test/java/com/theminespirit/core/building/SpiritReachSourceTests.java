package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SpiritReachSourceTests {
    @Test
    void current_SourceBound_ReturnsLoadedReach() {
        SpiritReach before = SpiritReach.current();
        try {
            SpiritReach data = new SpiritReach(7, 8, 9);

            SpiritReach.useSource(() -> data);

            assertEquals(
                    data,
                    SpiritReach.current(),
                    "Досягаемость из build.json обязана доходить до :mech:building без импорта загрузчика таблиц: "
                            + "иначе модуль снова тянет :content"
                            + " При смене current_SourceBound_ReturnsLoadedReach проверь это утверждение.");
        } finally {
            SpiritReach.useSource(() -> before);
        }
    }

    @Test
    void source_InvalidReach_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpiritReach(0, 1, 1),
                "Горизонтальная досягаемость меньше 1: дух не достал бы соседнюю клетку"
                        + " Иначе тест source_InvalidReach_IsRejected перестанет ловить эту поломку. При смене source_InvalidReach_IsRejected проверь это утверждение.");
    }
}

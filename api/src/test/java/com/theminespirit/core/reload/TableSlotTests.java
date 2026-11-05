package com.theminespirit.core.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TableSlotTests {
    @Test
    void current_FallbackReader_CalledOnlyOnce() {
        AtomicInteger reads = new AtomicInteger();
        TableSlot<String> slot = new TableSlot<>(() -> "builtin-" + reads.incrementAndGet());

        assertEquals("builtin-1", slot.current(), "Первое чтение обязано вернуть встроенное значение");
        assertEquals("builtin-1", slot.current(), "Повторное чтение обязано вернуть то же значение");
        assertEquals(1, reads.get(), "Встроенная таблица обязана читаться один раз: иначе горячий путь парсит файл");
    }

    @Test
    void use_OverriddenSource_ReplacesValue() {
        TableSlot<String> slot = new TableSlot<>(() -> "builtin");
        slot.use(() -> "reloaded");

        assertEquals("reloaded", slot.current(), "Владелец обязан видеть значение из источника перезагрузки");
    }

    @Test
    void use_NullSource_Throws() {
        TableSlot<String> slot = new TableSlot<>(() -> "builtin");

        assertThrows(
                NullPointerException.class,
                () -> slot.use(null),
                "Пустой источник подмены обязан падать: иначе перезагрузка молча перестанет быть видна");
    }

    @Test
    void current_NullFallbackValue_Throws() {
        TableSlot<String> slot = new TableSlot<>(() -> null);

        assertThrows(
                NullPointerException.class,
                slot::current,
                "Пустая встроенная таблица обязана падать: читатель не должен видеть null вместо схемы");
    }
}

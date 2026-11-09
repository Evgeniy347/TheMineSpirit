package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BreakLogKeysTests {
    private static final String KEY = "0,64,0|break|стройка";

    @Test
    void recently_KeyWithinWindow_IsDeduplicated() {
        BreakLogKeys keys = BreakLogKeys.empty();
        keys.remember(KEY, 100, 4096);

        assertTrue(
                keys.recently(KEY, 150, 200),
                "Повтор того же ключа внутри окна дедупликации не пишется в журнал второй раз");
        assertFalse(
                keys.recently(KEY, 301, 200),
                "После окна дедупликации ключ снова пишется: долгий инцидент виден новыми строками");
    }

    @Test
    void recently_KeyBeforeRecordedTick_IsNotStale() {
        BreakLogKeys keys = BreakLogKeys.empty();
        keys.remember(KEY, 500, 4096);

        assertFalse(
                keys.recently(KEY, 100, 200),
                "Отрицательная разница тиков не считается свежим повтором: иначе рестарт с меньшим временем "
                        + "глушит журнал навсегда");
    }

    @Test
    void remember_Overflow_KeepsBoundedCache() {
        BreakLogKeys keys = BreakLogKeys.empty();
        for (int i = 0; i < 5000; i++) {
            keys.remember("key-" + i, i, 4096);
        }

        assertTrue(keys.size() <= 4096, "Кэш ключей ограничен: без предела SavedData растёт без конца");
        assertTrue(
                keys.recently("key-4999", 5000, 200),
                "После сброса кэша свежий ключ снова пишется, а не теряется навсегда");
    }

    @Test
    void snapshot_CopiedKeys_SurviveRestart() {
        BreakLogKeys keys = BreakLogKeys.empty();
        keys.remember(KEY, 500, 4096);

        BreakLogKeys restored = BreakLogKeys.of(keys.snapshot());

        assertTrue(
                restored.recently(KEY, 600, 200),
                "Ключи из снимка SavedData продолжают дедупликацию после рестарта: журнал длинного инцидента "
                        + "не начинается заново");
    }
}

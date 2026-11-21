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
                "Повтор того же ключа внутри окна дедупликации не пишется в журнал второй раз"
                        + " Иначе тест recently_KeyWithinWindow_IsDeduplicated перестанет ловить эту поломку. При смене recently_KeyWithinWindow_IsDeduplicated проверь это утверждение.");
        assertFalse(
                keys.recently(KEY, 301, 200),
                "После окна дедупликации ключ снова пишется: долгий инцидент виден новыми строками"
                        + " Иначе тест recently_KeyWithinWindow_IsDeduplicated перестанет ловить эту поломку. При смене recently_KeyWithinWindow_IsDeduplicated проверь это утверждение.");
    }

    @Test
    void recently_KeyBeforeRecordedTick_IsNotStale() {
        BreakLogKeys keys = BreakLogKeys.empty();
        keys.remember(KEY, 500, 4096);

        assertFalse(
                keys.recently(KEY, 100, 200),
                "Отрицательная разница тиков не считается свежим повтором: иначе рестарт с меньшим временем "
                        + "глушит журнал навсегда"
                        + " При смене recently_KeyBeforeRecordedTick_IsNotStale проверь это утверждение.");
    }

    @Test
    void remember_Overflow_KeepsBoundedCache() {
        BreakLogKeys keys = BreakLogKeys.empty();
        for (int i = 0; i < 5000; i++) {
            keys.remember("key-" + i, i, 4096);
        }

        assertTrue(
                keys.size() <= 4096,
                "Кэш ключей ограничен: без предела SavedData растёт без конца"
                        + " Иначе тест remember_Overflow_KeepsBoundedCache перестанет ловить эту поломку. При смене remember_Overflow_KeepsBoundedCache проверь это утверждение.");
        assertTrue(
                keys.recently("key-4999", 5000, 200),
                "После сброса кэша свежий ключ снова пишется, а не теряется навсегда"
                        + " Иначе тест remember_Overflow_KeepsBoundedCache перестанет ловить эту поломку. При смене remember_Overflow_KeepsBoundedCache проверь это утверждение.");
    }

    @Test
    void snapshot_CopiedKeys_SurviveRestart() {
        BreakLogKeys keys = BreakLogKeys.empty();
        keys.remember(KEY, 500, 4096);

        BreakLogKeys restored = BreakLogKeys.of(keys.snapshot());

        assertTrue(
                restored.recently(KEY, 600, 200),
                "Ключи из снимка SavedData продолжают дедупликацию после рестарта: журнал длинного инцидента "
                        + "не начинается заново"
                        + " Иначе тест snapshot_CopiedKeys_SurviveRestart перестанет ловить эту поломку. При смене snapshot_CopiedKeys_SurviveRestart проверь это утверждение.");
    }
}

package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.script.api.CatalogLimits;
import org.junit.jupiter.api.Test;

class CatalogLimitsTests {
    @Test
    void acquireMaxAttempts_H1AgreedValue_IsThree() {
        assertEquals(
                3,
                CatalogLimits.ACQUIRE_MAX_ATTEMPTS,
                "Предел пересборок acquire согласован в H1 (tasks/28-acquire.md): больше трёх пересчётов "
                        + "платят повторным обследованием мира и не приближают цель"
                        + " Иначе тест acquireMaxAttempts_H1AgreedValue_IsThree перестанет ловить эту поломку. При смене acquireMaxAttempts_H1AgreedValue_IsThree проверь это утверждение.");
    }
}

package com.theminespirit.core.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BucketCatchTests {
    private static final FishingData DATA = new FishingData(2, 1, 5, 2, 8, 16);

    @Test
    void limit_RequestedAboveData_ClampsToData() {
        assertEquals(
                8,
                BucketCatch.limit(20, DATA),
                "Просьба поймать больше предела режется числом из fishing.json: за заход берут не больше M"
                        + " Иначе тест limit_RequestedAboveData_ClampsToData перестанет ловить эту поломку. При смене limit_RequestedAboveData_ClampsToData проверь это утверждение.");
    }

    @Test
    void limit_RequestedBelowData_KeepsRequested() {
        assertEquals(
                3,
                BucketCatch.limit(3, DATA),
                "Просьба ниже предела остаётся как есть: предел — верхняя граница, а не цель"
                        + " Иначе тест limit_RequestedBelowData_KeepsRequested перестанет ловить эту поломку. При смене limit_RequestedBelowData_KeepsRequested проверь это утверждение.");
    }

    @Test
    void limit_NonPositiveRequested_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BucketCatch.limit(0, DATA),
                "Заход с нулём рыб обязан отклоняться: ловить нечего, задача не ставится"
                        + " Иначе тест limit_NonPositiveRequested_IsRejected перестанет ловить эту поломку. При смене limit_NonPositiveRequested_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> BucketCatch.limit(1, null),
                "Предел без данных рыбалки не считается: число живёт в fishing.json"
                        + " Иначе тест limit_NonPositiveRequested_IsRejected перестанет ловить эту поломку. При смене limit_NonPositiveRequested_IsRejected проверь это утверждение.");
    }
}

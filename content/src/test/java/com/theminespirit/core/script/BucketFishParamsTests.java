package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.script.api.CatalogLimits;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BucketFishParamsTests {

    @Test
    void from_EmptyParams_AppliesCatalogDefaults() {
        BucketFishParams params = BucketFishParams.from(Map.of());

        assertEquals(
                CatalogLimits.FISH_COUNT_DEFAULT,
                params.count(),
                "Без числа рыбы ведро ловит по умолчанию каталога: предел M дополнительно режет fishing.json"
                        + " Иначе тест from_EmptyParams_AppliesCatalogDefaults перестанет ловить эту поломку. При смене from_EmptyParams_AppliesCatalogDefaults проверь это утверждение.");
        assertEquals(
                CatalogLimits.FISH_RADIUS_DEFAULT,
                params.radius(),
                "Без радиуса берётся умолчание каталога: область поиска рыбы задаётся в одном месте"
                        + " Иначе тест from_EmptyParams_AppliesCatalogDefaults перестанет ловить эту поломку. При смене from_EmptyParams_AppliesCatalogDefaults проверь это утверждение.");
    }

    @Test
    void from_CountAndRadius_Accepted() {
        assertEquals(
                new BucketFishParams(4, 8),
                BucketFishParams.from(Map.of("count", 4, "radius", 8)),
                "bucket_fish обязан принять число рыбы и радиус: по ним скрипт ищет рыбу и ловит ведром"
                        + " Иначе тест from_CountAndRadius_Accepted перестанет ловить эту поломку. При смене from_CountAndRadius_Accepted проверь это утверждение.");
    }

    @Test
    void from_CountOutsideRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BucketFishParams.from(Map.of("count", 0, "radius", 8)),
                "Ноль рыб за заход обязан отклоняться: пустая ловля ведром не ставится"
                        + " Иначе тест from_CountOutsideRange_IsRejected перестанет ловить эту поломку. При смене from_CountOutsideRange_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> BucketFishParams.from(Map.of("count", 300, "radius", 8)),
                "Число выше предела каталога обязано отклоняться: столько рыбы за заход не поймать"
                        + " Иначе тест from_CountOutsideRange_IsRejected перестанет ловить эту поломку. При смене from_CountOutsideRange_IsRejected проверь это утверждение.");
    }

    @Test
    void from_RadiusOutsideRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BucketFishParams.from(Map.of("count", 1, "radius", 0)),
                "Нулевой радиус обязан отклоняться: под духом рыбу не ищут"
                        + " Иначе тест from_RadiusOutsideRange_IsRejected перестанет ловить эту поломку. При смене from_RadiusOutsideRange_IsRejected проверь это утверждение.");
    }
}

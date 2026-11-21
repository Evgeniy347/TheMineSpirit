package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.script.api.CatalogLimits;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FishingParamsTests {
    @Test
    void from_EmptyParams_AppliesCatalogDefaults() {
        FishingParams params = FishingParams.from(Map.of());

        assertEquals(
                CatalogLimits.FISH_COUNT_DEFAULT,
                params.count(),
                "Без числа рыбы берётся умолчание каталога: сколько ловить, решает каталог, а не маппинг"
                        + " Иначе тест from_EmptyParams_AppliesCatalogDefaults перестанет ловить эту поломку. При смене from_EmptyParams_AppliesCatalogDefaults проверь это утверждение.");
        assertEquals(
                CatalogLimits.FISH_DURATION_DEFAULT,
                params.durationTicks(),
                "Без времени ловли берётся умолчание каталога: иначе скрипт не знает, когда кончать"
                        + " При смене from_EmptyParams_AppliesCatalogDefaults проверь это утверждение.");
        assertEquals(
                CatalogLimits.FISH_RADIUS_DEFAULT,
                params.radius(),
                "Без радиуса берётся умолчание каталога: область поиска воды задаётся в одном месте"
                        + " Иначе тест from_EmptyParams_AppliesCatalogDefaults перестанет ловить эту поломку. При смене from_EmptyParams_AppliesCatalogDefaults проверь это утверждение.");
    }

    @Test
    void from_AllParamsPresent_ReturnsValues() {
        FishingParams params = FishingParams.from(Map.of("count", 3, "duration_ticks", 600, "radius", 8));

        assertEquals(
                new FishingParams(3, 600, 8),
                params,
                "fish обязан принять число рыбы, время ловли и радиус: по ним скрипт ищет воду и ловит"
                        + " Иначе тест from_AllParamsPresent_ReturnsValues перестанет ловить эту поломку. При смене from_AllParamsPresent_ReturnsValues проверь это утверждение.");
    }

    @Test
    void from_CountOutsideRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FishingParams.from(Map.of("count", 0, "duration_ticks", 600, "radius", 8)),
                "count ниже минимума обязан быть отклонён: поймать ноль рыбы — пустая команда"
                        + " Иначе тест from_CountOutsideRange_IsRejected перестанет ловить эту поломку. При смене from_CountOutsideRange_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> FishingParams.from(Map.of("count", 257, "duration_ticks", 600, "radius", 8)),
                "count выше максимума обязан быть отклонён: столько рыбы за запуск не поймать"
                        + " Иначе тест from_CountOutsideRange_IsRejected перестанет ловить эту поломку. При смене from_CountOutsideRange_IsRejected проверь это утверждение.");
    }

    @Test
    void from_DurationOutsideRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FishingParams.from(Map.of("count", 1, "duration_ticks", 19, "radius", 8)),
                "duration_ticks ниже минимума обязан быть отклонён: за секунду рыба не клюнет"
                        + " Иначе тест from_DurationOutsideRange_IsRejected перестанет ловить эту поломку. При смене from_DurationOutsideRange_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> FishingParams.from(Map.of("count", 1, "duration_ticks", 6001, "radius", 8)),
                "duration_ticks выше максимума обязан быть отклонён: иначе дух рыбачит бесконечно"
                        + " При смене from_DurationOutsideRange_IsRejected проверь это утверждение.");
    }

    @Test
    void from_RadiusOutsideRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FishingParams.from(Map.of("count", 1, "duration_ticks", 600, "radius", 0)),
                "Нулевой радиус обязан быть отклонён: под духом воды не ищут"
                        + " Иначе тест from_RadiusOutsideRange_IsRejected перестанет ловить эту поломку. При смене from_RadiusOutsideRange_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> FishingParams.from(Map.of("count", 1, "duration_ticks", 600, "radius", 33)),
                "Радиус выше максимума обязан быть отклонён: поиск воды на полкарты кладёт тик"
                        + " Иначе тест from_RadiusOutsideRange_IsRejected перестанет ловить эту поломку. При смене from_RadiusOutsideRange_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> FishingParams.from(null),
                "FishingParams.from(null) обязан отказывать: без карты параметров маппинг только читает мир"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}

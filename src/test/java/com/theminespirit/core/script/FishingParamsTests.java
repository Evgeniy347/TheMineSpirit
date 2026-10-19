package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FishingParamsTests {
    @Test
    void from_EmptyParams_AppliesCatalogDefaults() {
        FishingParams params = FishingParams.from(Map.of());

        assertEquals(
                CatalogLimits.FISH_COUNT_DEFAULT,
                params.count(),
                "Без числа рыбы берётся умолчание каталога: сколько ловить, решает каталог, а не маппинг");
        assertEquals(
                CatalogLimits.FISH_DURATION_DEFAULT,
                params.durationTicks(),
                "Без времени ловли берётся умолчание каталога: иначе скрипт не знает, когда кончать");
        assertEquals(
                CatalogLimits.FISH_RADIUS_DEFAULT,
                params.radius(),
                "Без радиуса берётся умолчание каталога: область поиска воды задаётся в одном месте");
    }

    @Test
    void from_AllParamsPresent_ReturnsValues() {
        FishingParams params = FishingParams.from(Map.of("count", 3, "duration_ticks", 600, "radius", 8));

        assertEquals(
                new FishingParams(3, 600, 8),
                params,
                "fish обязан принять число рыбы, время ловли и радиус: по ним скрипт ищет воду и ловит");
    }

    @Test
    void from_CountOutsideRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FishingParams.from(Map.of("count", 0, "duration_ticks", 600, "radius", 8)),
                "count ниже минимума обязан быть отклонён: поймать ноль рыбы — пустая команда");
        assertThrows(
                IllegalArgumentException.class,
                () -> FishingParams.from(Map.of("count", 257, "duration_ticks", 600, "radius", 8)),
                "count выше максимума обязан быть отклонён: столько рыбы за запуск не поймать");
    }

    @Test
    void from_DurationOutsideRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FishingParams.from(Map.of("count", 1, "duration_ticks", 19, "radius", 8)),
                "duration_ticks ниже минимума обязан быть отклонён: за секунду рыба не клюнет");
        assertThrows(
                IllegalArgumentException.class,
                () -> FishingParams.from(Map.of("count", 1, "duration_ticks", 6001, "radius", 8)),
                "duration_ticks выше максимума обязан быть отклонён: иначе дух рыбачит бесконечно");
    }

    @Test
    void from_RadiusOutsideRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FishingParams.from(Map.of("count", 1, "duration_ticks", 600, "radius", 0)),
                "Нулевой радиус обязан быть отклонён: под духом воды не ищут");
        assertThrows(
                IllegalArgumentException.class,
                () -> FishingParams.from(Map.of("count", 1, "duration_ticks", 600, "radius", 33)),
                "Радиус выше максимума обязан быть отклонён: поиск воды на полкарты кладёт тик");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> FishingParams.from(null),
                "FishingParams.from(null) обязан отказывать: без карты параметров маппинг только читает мир");
    }
}

package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WanderParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        WanderParams params = WanderParams.from(Map.of("radius", 8, "duration_ticks", 600));

        assertEquals(
                new WanderParams(8, 600),
                params,
                "wander обязан сохранить радиус и длительность: по ним скрипт выбирает точки и знает, когда кончить");
    }

    @Test
    void from_MissingRadius_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WanderParams.from(Map.of("duration_ticks", 600)),
                "wander без радиуса обязан отказывать: валидатор плана подставляет значение по умолчанию "
                        + "из каталога до запуска, а без него скрипт не знает границу зоны");
    }

    @Test
    void from_RadiusOutOfRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WanderParams.from(Map.of("radius", 33, "duration_ticks", 600)),
                "radius больше 32 обязан отвергаться: лимит держит духа в тикающей зоне");
    }

    @Test
    void from_DurationBelowMin_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WanderParams.from(Map.of("radius", 8, "duration_ticks", 19)),
                "Длительность короче 20 тиков обязана отвергаться: за секунду дух не успевает сдвинуться, "
                        + "а стратег решит, что блуждание прошло");
    }

    @Test
    void from_DurationAboveMax_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WanderParams.from(Map.of("radius", 8, "duration_ticks", 6001)),
                "Длительность дольше 6000 тиков обязана отвергаться: лимит держит блуждание в пределах 5 минут");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> WanderParams.from(null),
                "WanderParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира");
    }
}

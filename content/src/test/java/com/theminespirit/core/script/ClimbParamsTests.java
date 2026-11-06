package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ClimbParamsTests {
    @Test
    void from_EmptyMap_ClimbsUpByDefault() {
        assertFalse(
                ClimbParams.from(Map.of()).down(),
                "climb без параметра обязан лезть вверх: спуск — явное намерение, иначе дух уходил бы вниз "
                        + "там, где его ждали наверху");
    }

    @Test
    void from_DownTrue_Parsed() {
        assertTrue(
                ClimbParams.from(Map.of("down", true)).down(),
                "down=true обязан дойти до скрипта: по нему выбирается низ колонны, а не верх");
    }

    @Test
    void from_DownNotBoolean_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ClimbParams.from(Map.of("down", "yes")),
                "небулев down обязан отвергаться: иначе 'yes' молча стал бы спуском или подъёмом по случайности");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> ClimbParams.from(null),
                "ClimbParams.from(null) обязан отказывать: без карты параметров маппинг только читает план");
    }
}

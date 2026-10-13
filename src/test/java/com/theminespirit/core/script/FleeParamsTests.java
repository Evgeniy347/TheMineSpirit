package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FleeParamsTests {
    @Test
    void from_EmptyMap_ReturnsParams() {
        assertEquals(new FleeParams(), FleeParams.from(Map.of()),
                "flee обязан собираться из пустого набора параметров: цель бегства — близкая угроза, "
                        + "а не названное существо");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(NullPointerException.class, () -> FleeParams.from(null),
                "FleeParams.from(null) обязан отказывать: без карты параметров маппинг только читает план");
    }
}

package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ClearCampParamsTests {
    @Test
    void from_AnyMap_AcceptsWithoutParams() {
        assertEquals(new ClearCampParams(), ClearCampParams.from(Map.of()),
                "clear_camp без параметров обязан разбираться: снятие лагеря не требует точек и радиуса");
        assertEquals(new ClearCampParams(), ClearCampParams.from(Map.of("radius", 8)),
                "clear_camp обязан игнорировать лишние параметры: снятие лагеря не зависит от радиуса");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(NullPointerException.class, () -> ClearCampParams.from(null),
                "ClearCampParams.from(null) обязан отказывать: без карты параметров маппинг только читает план");
    }
}

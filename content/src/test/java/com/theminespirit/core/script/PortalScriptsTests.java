package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.script.api.CastObsidianParams;
import com.theminespirit.core.script.api.LightPortalParams;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortalScriptsTests {
    @Test
    void castParams_FromMap_ReadsCountAndRadius() {
        CastObsidianParams params = CastObsidianParams.from(Map.of("count", 3, "radius", 12));

        assertEquals(
                3,
                params.count(),
                "Число обсидиана берётся из плана: доска просит не один, когда нужен запас"
                        + " Иначе тест castParams_FromMap_ReadsCountAndRadius перестанет ловить эту поломку. При смене castParams_FromMap_ReadsCountAndRadius проверь это утверждение.");
        assertEquals(
                12,
                params.radius(),
                "Радиус поиска лавы берётся из плана"
                        + " Иначе тест castParams_FromMap_ReadsCountAndRadius перестанет ловить эту поломку. При смене castParams_FromMap_ReadsCountAndRadius проверь это утверждение.");
    }

    @Test
    void lightParams_FromMap_ReadsRadius() {
        LightPortalParams params = LightPortalParams.from(Map.of("radius", 8));

        assertEquals(
                8,
                params.radius(),
                "Радиус поиска рамки портала берётся из плана"
                        + " Иначе тест lightParams_FromMap_ReadsRadius перестанет ловить эту поломку. При смене lightParams_FromMap_ReadsRadius проверь это утверждение.");
    }
}

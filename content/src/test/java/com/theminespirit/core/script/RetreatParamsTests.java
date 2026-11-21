package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RetreatParamsTests {
    @Test
    void from_EmptyMap_ReturnsParams() {
        assertEquals(
                new RetreatParams(),
                RetreatParams.from(Map.of()),
                "retreat обязан собираться из пустого набора параметров: точка отхода берётся из памяти духа, "
                        + "а не из плана"
                        + " Иначе тест from_EmptyMap_ReturnsParams перестанет ловить эту поломку. При смене from_EmptyMap_ReturnsParams проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> RetreatParams.from(null),
                "RetreatParams.from(null) обязан отказывать: без карты параметров маппинг только читает план"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}

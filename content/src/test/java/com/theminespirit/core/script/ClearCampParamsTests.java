package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ClearCampParamsTests {
    @Test
    void from_AnyMap_AcceptsWithoutParams() {
        assertEquals(
                new ClearCampParams(),
                ClearCampParams.from(Map.of()),
                "clear_camp без параметров обязан разбираться: снятие лагеря не требует точек и радиуса"
                        + " Иначе тест from_AnyMap_AcceptsWithoutParams перестанет ловить эту поломку. При смене from_AnyMap_AcceptsWithoutParams проверь это утверждение.");
        assertEquals(
                new ClearCampParams(),
                ClearCampParams.from(Map.of("radius", 8)),
                "clear_camp обязан игнорировать лишние параметры: снятие лагеря не зависит от радиуса"
                        + " Иначе тест from_AnyMap_AcceptsWithoutParams перестанет ловить эту поломку. При смене from_AnyMap_AcceptsWithoutParams проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> ClearCampParams.from(null),
                "ClearCampParams.from(null) обязан отказывать: без карты параметров маппинг только читает план"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}

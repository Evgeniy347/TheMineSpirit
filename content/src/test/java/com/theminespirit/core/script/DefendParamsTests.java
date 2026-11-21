package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DefendParamsTests {
    @Test
    void from_TargetPresent_KeepsTarget() {
        DefendParams params = DefendParams.from(Map.of("target", "player:Alex"));

        assertEquals(
                "player:Alex",
                params.target(),
                "defend обязан сохранить подопечного: без его имени тактика не знает, кого охранять"
                        + " Иначе тест from_TargetPresent_KeepsTarget перестанет ловить эту поломку. При смене from_TargetPresent_KeepsTarget проверь это утверждение.");
    }

    @Test
    void from_MissingTarget_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DefendParams.from(Map.of()),
                "defend без цели обязан отказывать: подопечный обязателен, в отличие от attack, "
                        + "который бьёт ближайшую угрозу"
                        + " Иначе тест from_MissingTarget_IsRejected перестанет ловить эту поломку. При смене from_MissingTarget_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("target"),
                "Отказ обязан называть недостающий параметр 'target'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MissingTarget_IsRejected перестанет ловить эту поломку. При смене from_MissingTarget_IsRejected проверь это утверждение.");
    }

    @Test
    void from_BlankTarget_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DefendParams.from(Map.of("target", "  ")),
                "defend с пустой целью обязан отказывать: пустую строку не сопоставить ни с одним существом"
                        + " Иначе тест from_BlankTarget_IsRejected перестанет ловить эту поломку. При смене from_BlankTarget_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> DefendParams.from(null),
                "DefendParams.from(null) обязан отказывать: без карты параметров маппинг только читает план"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}

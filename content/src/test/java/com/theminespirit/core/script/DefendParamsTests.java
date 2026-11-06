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
                "defend обязан сохранить подопечного: без его имени тактика не знает, кого охранять");
    }

    @Test
    void from_MissingTarget_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DefendParams.from(Map.of()),
                "defend без цели обязан отказывать: подопечный обязателен, в отличие от attack, "
                        + "который бьёт ближайшую угрозу");

        assertTrue(
                error.getMessage().contains("target"),
                "Отказ обязан называть недостающий параметр 'target'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_BlankTarget_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DefendParams.from(Map.of("target", "  ")),
                "defend с пустой целью обязан отказывать: пустую строку не сопоставить ни с одним существом");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> DefendParams.from(null),
                "DefendParams.from(null) обязан отказывать: без карты параметров маппинг только читает план");
    }
}

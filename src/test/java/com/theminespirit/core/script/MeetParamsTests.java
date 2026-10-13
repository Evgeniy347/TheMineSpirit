package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MeetParamsTests {
    @Test
    void from_TargetPresent_KeepsTarget() {
        MeetParams params = MeetParams.from(Map.of("target", "player:Steve"));

        assertEquals("player:Steve", params.target(),
                "meet обязан сохранить цель: по строке адаптер находит игрока или духа, к которым подойти");
    }

    @Test
    void from_MissingTarget_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MeetParams.from(Map.of()),
                "meet без цели обязан отказывать: непонятно, с кем встретиться, идти некуда");

        assertTrue(error.getMessage().contains("target"),
                "Отказ обязан называть недостающий параметр 'target'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_BlankTarget_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MeetParams.from(Map.of("target", "")),
                "meet с пустой целью обязан отказывать: пустую строку не сопоставить ни с одним существом");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(NullPointerException.class, () -> MeetParams.from(null),
                "MeetParams.from(null) обязан отказывать: без карты параметров маппинг только читает план");
    }
}

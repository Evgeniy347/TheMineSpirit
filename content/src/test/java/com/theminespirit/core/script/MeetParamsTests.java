package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetParamsTests {
    @Test
    void from_TargetPresent_KeepsTarget() {
        MeetParams params = MeetParams.from(Map.of("target", "player:Steve"));

        assertEquals(
                "player:Steve",
                params.target(),
                "meet обязан сохранить цель: по строке адаптер находит игрока или духа, к которым подойти"
                        + " Иначе тест from_TargetPresent_KeepsTarget перестанет ловить эту поломку. При смене from_TargetPresent_KeepsTarget проверь это утверждение.");
    }

    @Test
    void from_SpiritTarget_IsAccepted() {
        String uuid = UUID.randomUUID().toString();
        MeetParams params = MeetParams.from(Map.of("target", "spirit:" + uuid));

        assertEquals(
                "spirit:" + uuid,
                params.target(),
                "meet обязан принимать духа по UUID: встреча бывает не только с игроком"
                        + " Иначе тест from_SpiritTarget_IsAccepted перестанет ловить эту поломку. При смене from_SpiritTarget_IsAccepted проверь это утверждение.");
    }

    @Test
    void from_MonsterKeyword_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> MeetParams.from(Map.of("target", "hostile")),
                "meet с видом hostile обязан отказывать: с монстром не встречаются, а вида цели у meet нет"
                        + " Иначе тест from_MonsterKeyword_IsRejected перестанет ловить эту поломку. При смене from_MonsterKeyword_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("target"),
                "Отказ обязан называть параметр 'target'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MonsterKeyword_IsRejected перестанет ловить эту поломку. При смене from_MonsterKeyword_IsRejected проверь это утверждение.");
    }

    @Test
    void from_AnyKeyword_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MeetParams.from(Map.of("target", "nearest")),
                "meet с видом nearest обязан отказывать: неясно, к кому именно идти на встречу"
                        + " Иначе тест from_AnyKeyword_IsRejected перестанет ловить эту поломку. При смене from_AnyKeyword_IsRejected проверь это утверждение.");
    }

    @Test
    void from_UnparsableTarget_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MeetParams.from(Map.of("target", "кто-то непонятный")),
                "meet с нераспознаваемой строкой обязан отказывать: цель не сопоставить ни с кем"
                        + " Иначе тест from_UnparsableTarget_IsRejected перестанет ловить эту поломку. При смене from_UnparsableTarget_IsRejected проверь это утверждение.");
    }

    @Test
    void from_MissingTarget_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> MeetParams.from(Map.of()),
                "meet без цели обязан отказывать: непонятно, с кем встретиться, идти некуда"
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
                () -> MeetParams.from(Map.of("target", "")),
                "meet с пустой целью обязан отказывать: пустую строку не сопоставить ни с одним существом"
                        + " Иначе тест from_BlankTarget_IsRejected перестанет ловить эту поломку. При смене from_BlankTarget_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> MeetParams.from(null),
                "MeetParams.from(null) обязан отказывать: без карты параметров маппинг только читает план"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}

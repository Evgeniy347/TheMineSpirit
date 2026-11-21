package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportParamsTests {
    @Test
    void from_MissingAddressee_MeansAllNearby() {
        ReportParams params = ReportParams.from(Map.of());

        assertEquals(
                Optional.empty(),
                params.to(),
                "report без 'to' обязан докладывать всем в радиусе слышимости: доклад не требует адресата"
                        + " Иначе тест from_MissingAddressee_MeansAllNearby перестанет ловить эту поломку. При смене from_MissingAddressee_MeansAllNearby проверь это утверждение.");
    }

    @Test
    void from_BlankAddressee_MeansAllNearby() {
        ReportParams params = ReportParams.from(Map.of("to", " "));

        assertEquals(
                Optional.empty(),
                params.to(),
                "report с пустым 'to' считается докладом без адресата: пустая строка не называет игрока"
                        + " Иначе тест from_BlankAddressee_MeansAllNearby перестанет ловить эту поломку. При смене from_BlankAddressee_MeansAllNearby проверь это утверждение.");
    }

    @Test
    void from_PlayerAddressee_IsKept() {
        ReportParams params = ReportParams.from(Map.of("to", "player:Steve"));

        assertEquals(
                Optional.of("player:Steve"),
                params.to(),
                "report обязан сохранить названного игрока: доклад идёт в его чат"
                        + " Иначе тест from_PlayerAddressee_IsKept перестанет ловить эту поломку. При смене from_PlayerAddressee_IsKept проверь это утверждение.");
    }

    @Test
    void from_SpiritAddressee_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ReportParams.from(Map.of("to", "spirit:" + UUID.randomUUID())),
                "report духу обязан отказывать: у духа нет чата, доклад некому показать"
                        + " Иначе тест from_SpiritAddressee_IsRejected перестанет ловить эту поломку. При смене from_SpiritAddressee_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("to"),
                "Отказ обязан называть параметр 'to'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_SpiritAddressee_IsRejected перестанет ловить эту поломку. При смене from_SpiritAddressee_IsRejected проверь это утверждение.");
    }

    @Test
    void from_MonsterKeywordAddressee_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReportParams.from(Map.of("to", "hostile")),
                "report виду цели обязан отказывать: доклад в чат не адресуется мобу"
                        + " Иначе тест from_MonsterKeywordAddressee_IsRejected перестанет ловить эту поломку. При смене from_MonsterKeywordAddressee_IsRejected проверь это утверждение.");
    }

    @Test
    void from_UnparsableAddressee_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReportParams.from(Map.of("to", "кто-то непонятный")),
                "report с нераспознаваемым адресатом обязан отказывать: докладывать некому"
                        + " Иначе тест from_UnparsableAddressee_IsRejected перестанет ловить эту поломку. При смене from_UnparsableAddressee_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> ReportParams.from(null),
                "ReportParams.from(null) обязан отказывать: без карты параметров скрипт не разобрать"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}

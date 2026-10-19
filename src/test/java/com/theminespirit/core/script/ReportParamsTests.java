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
                "report без 'to' обязан докладывать всем в радиусе слышимости: доклад не требует адресата");
    }

    @Test
    void from_BlankAddressee_MeansAllNearby() {
        ReportParams params = ReportParams.from(Map.of("to", " "));

        assertEquals(
                Optional.empty(),
                params.to(),
                "report с пустым 'to' считается докладом без адресата: пустая строка не называет игрока");
    }

    @Test
    void from_PlayerAddressee_IsKept() {
        ReportParams params = ReportParams.from(Map.of("to", "player:Steve"));

        assertEquals(
                Optional.of("player:Steve"),
                params.to(),
                "report обязан сохранить названного игрока: доклад идёт в его чат");
    }

    @Test
    void from_SpiritAddressee_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ReportParams.from(Map.of("to", "spirit:" + UUID.randomUUID())),
                "report духу обязан отказывать: у духа нет чата, доклад некому показать");

        assertTrue(
                error.getMessage().contains("to"),
                "Отказ обязан называть параметр 'to'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_MonsterKeywordAddressee_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReportParams.from(Map.of("to", "hostile")),
                "report виду цели обязан отказывать: доклад в чат не адресуется мобу");
    }

    @Test
    void from_UnparsableAddressee_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReportParams.from(Map.of("to", "кто-то непонятный")),
                "report с нераспознаваемым адресатом обязан отказывать: докладывать некому");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> ReportParams.from(null),
                "ReportParams.from(null) обязан отказывать: без карты параметров скрипт не разобрать");
    }
}

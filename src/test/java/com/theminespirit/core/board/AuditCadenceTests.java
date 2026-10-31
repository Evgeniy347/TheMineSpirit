package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuditCadenceTests {
    private static final Path TICKS = Path.of("src/test/resources/live/hutreopen/ticks.json");

    @Test
    void due_LiveBoardTickOffset_FiresFirstTimeAndAfterInterval() throws IOException {
        long boardTick = ticks().get("observe14BoardTick").asLong();
        long interval = ticks().get("auditIntervalTicks").asLong();

        assertTrue(
                AuditCadence.due(AuditCadence.NEVER, boardTick, interval),
                "Первый обход после старта обязан случиться на ближайшем тике доски: иначе повреждение хижины "
                        + "стоит без обхода до перезапуска");
        assertFalse(
                AuditCadence.due(boardTick, boardTick + 100, interval),
                "Обход не повторяется на каждом тике доски: иначе сканирование дома идёт 20 раз в секунду");
        assertTrue(
                AuditCadence.due(boardTick, boardTick + interval, interval),
                "Через интервал обход обязан случиться при любом смещении тика доски: в observe14 тики доски "
                        + "шли с шагом 100 от смещения " + (boardTick % 100)
                        + ", поэтому проверка «тик % интервал == 0» не срабатывала ни разу (A1/N8)");
    }

    @Test
    void due_LiveWarnRepeatStep_FiresAfterRepeat() throws IOException {
        long boardTick = ticks().get("observe14BoardTick").asLong();
        long repeat = ticks().get("warnRepeatTicks").asLong();

        assertFalse(
                AuditCadence.due(boardTick, boardTick + repeat - 100, repeat),
                "Напоминание о повреждении не чаще своего шага: иначе лог забивается каждой минутой");
        assertTrue(
                AuditCadence.due(boardTick, boardTick + repeat, repeat),
                "Напоминание о повреждении обязано выйти через свой шаг при живом смещении тика доски");
    }

    @Test
    void due_StepBelowOne_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AuditCadence.due(AuditCadence.NEVER, 100, 0),
                "Нулевой шаг обхода — ошибка данных: молчаливый обход каждого тика сжёг бы серверный тик");
    }

    private static JsonNode ticks() throws IOException {
        return new ObjectMapper().readTree(Files.readString(TICKS, StandardCharsets.UTF_8));
    }
}

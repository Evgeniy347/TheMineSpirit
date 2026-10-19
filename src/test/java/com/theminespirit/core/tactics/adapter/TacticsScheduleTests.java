package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TacticsScheduleTests {
    private static final int INTERVAL = TacticsAdapterSettings.defaults().evalIntervalTicks();

    record Row(String name, long evaluatedAt, long eventAt, long now, boolean hurt, boolean forced, boolean expected) {}

    static Stream<Row> rows() {
        return Stream.of(
                new Row("первый вызов: оценок ещё не было", -1, -1, 0, false, false, true),
                new Row("прошёл 1 тик из 10", 100, -1, 101, false, false, false),
                new Row("прошло 9 тиков из 10", 100, -1, 109, false, false, false),
                new Row("прошло ровно 10 тиков", 100, -1, 110, false, false, true),
                new Row("прошло больше интервала", 100, -1, 150, false, false, true),
                new Row("урон на 2-й тик: событийный вызов", 100, -1, 102, true, false, true),
                new Row(
                        "урон на 1-й тик подряд за вызовом по событию: зазор не вышел",
                        100,
                        100,
                        101,
                        true,
                        false,
                        false),
                new Row("урон спустя 5 тиков после прошлого события", 100, 100, 105, true, false, true),
                new Row("урон спустя 4 тика после прошлого события", 100, 100, 104, true, false, false),
                new Row("принудительный вызов (возобновление после политики)", 100, -1, 101, false, true, true),
                new Row("принудительный вызов игнорирует зазор события", 100, 100, 101, true, true, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void due_State_GivesExpectedAnswer(Row row) {
        TacticsSchedule schedule = new TacticsSchedule(INTERVAL);
        if (row.evaluatedAt() >= 0) {
            schedule.evaluated(row.evaluatedAt(), false);
        }
        if (row.eventAt() >= 0) {
            schedule.evaluated(row.eventAt(), true);
        }

        assertEquals(
                row.expected(),
                schedule.due(row.now(), row.hurt(), row.forced()),
                "Расписание («" + row.name() + "»): оценка раз в " + INTERVAL
                        + " тиков, урон вызывает её сразу, но не чаще раза в " + TacticsSchedule.HURT_MIN_GAP_TICKS
                        + " тиков; иначе тактика душит сервер или опаздывает");
    }

    @Test
    void due_NeverEvaluated_ReportsNotEvaluatedYet() {
        TacticsSchedule schedule = new TacticsSchedule(INTERVAL);

        assertFalse(schedule.everEvaluated(), "Новое расписание ещё не оценивало: первый вызов обязан сработать сразу");
        schedule.evaluated(5, false);
        assertTrue(schedule.everEvaluated(), "После оценки расписание помнит, что она была");
    }

    @Test
    void constructor_ZeroInterval_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TacticsSchedule(0),
                "Интервал 0 означал бы оценку на каждом тике: настройки обязаны отвергнуть его заранее");
    }
}

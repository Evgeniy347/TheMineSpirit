package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoordinatorTuningTests {
    @Test
    void snapshotInterval_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(10L, CoordinatorTuning.SNAPSHOT_INTERVAL_TICKS,
                "Снимок раз в 10 тиков: позже — координатор поздно видит угрозу, чаще — лишние запросы сущностей");
    }

    @Test
    void defenseRadius_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(16.0, CoordinatorTuning.DEFENSE_RADIUS,
                "Радиус обороны 16 — как ALLY_RADIUS и лимит follow.radius: сектора держат защитников у базы");
    }

    @Test
    void defensePostRadius_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(3.0, CoordinatorTuning.DEFENSE_POST_RADIUS,
                "Радиус удержания поста 3: защитник держится у поста, а не уходит через весь круг");
    }

    @Test
    void workLease_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(600L, CoordinatorTuning.WORK_LEASE_TICKS,
                "Срок брони 600 тиков (30 с): дольше — цель занята без дела, короче — работа перераспределяется "
                        + "под руками");
    }

    @Test
    void maxAssist_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(2, CoordinatorTuning.MAX_ASSIST,
                "Не больше двух помощников: больше — сектора пустеют, меньше — защитники гибнут");
    }

    @Test
    void snapshotStale_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(40L, CoordinatorTuning.SNAPSHOT_STALE_TICKS,
                "Снимок просрочен через 40 тиков (2 с): выбывший дух не держит пост и цель вечно");
    }

    @Test
    void snapshotGroupQueries_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(1, CoordinatorTuning.SNAPSHOT_GROUP_QUERIES,
                "Перечисление поселения — один запрос за пересборку: чаще — лишние обходы всех сущностей уровня");
    }

    @Test
    void snapshotSpiritQueries_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(1, CoordinatorTuning.SNAPSHOT_SPIRIT_QUERIES,
                "Чтение духа — один запрос за пересборку: чаще — счётчик координатора растёт с духами, а не с периодом");
    }

    @Test
    void everyConstant_AllValues_PositiveAndFinite() {
        assertTrue(CoordinatorTuning.SNAPSHOT_INTERVAL_TICKS > 0, "Интервал снимка положителен");
        assertTrue(CoordinatorTuning.DEFENSE_RADIUS > 0 && Double.isFinite(CoordinatorTuning.DEFENSE_RADIUS),
                "Радиус обороны положителен и конечен");
        assertTrue(CoordinatorTuning.DEFENSE_POST_RADIUS > 0 && Double.isFinite(CoordinatorTuning.DEFENSE_POST_RADIUS),
                "Радиус поста положителен и конечен");
        assertTrue(CoordinatorTuning.WORK_LEASE_TICKS > 0, "Срок брони положителен");
        assertTrue(CoordinatorTuning.MAX_ASSIST > 0, "Предел помощников положителен");
        assertTrue(CoordinatorTuning.SNAPSHOT_STALE_TICKS > 0, "Просроченность снимка положительна");
        assertTrue(CoordinatorTuning.SNAPSHOT_GROUP_QUERIES > 0, "Перечисление поселения делает запросы");
        assertTrue(CoordinatorTuning.SNAPSHOT_SPIRIT_QUERIES > 0, "Чтение духа делает запросы");
    }
}

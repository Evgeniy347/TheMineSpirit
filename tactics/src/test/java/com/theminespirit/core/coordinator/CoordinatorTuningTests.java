package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoordinatorTuningTests {
    @Test
    void snapshotInterval_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                10L,
                CoordinatorTuning.SNAPSHOT_INTERVAL_TICKS,
                "Снимок раз в 10 тиков: позже — координатор поздно видит угрозу, чаще — лишние запросы сущностей"
                        + " Иначе тест snapshotInterval_DeclaredValue_MatchesTuningLiteral перестанет ловить эту поломку. При смене snapshotInterval_DeclaredValue_MatchesTuningLiteral проверь это утверждение.");
    }

    @Test
    void defenseRadius_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                16.0,
                CoordinatorTuning.DEFENSE_RADIUS,
                "Радиус обороны 16 — как радиус сбора духов и лимит follow.radius: сектора держат защитников у базы"
                        + " Иначе тест defenseRadius_DeclaredValue_MatchesTuningLiteral перестанет ловить эту поломку. При смене defenseRadius_DeclaredValue_MatchesTuningLiteral проверь это утверждение.");
    }

    @Test
    void defensePostRadius_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                3.0,
                CoordinatorTuning.DEFENSE_POST_RADIUS,
                "Радиус удержания поста 3: защитник держится у поста, а не уходит через весь круг"
                        + " Иначе тест defensePostRadius_DeclaredValue_MatchesTuningLiteral перестанет ловить эту поломку. При смене defensePostRadius_DeclaredValue_MatchesTuningLiteral проверь это утверждение.");
    }

    @Test
    void workLease_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                600L,
                CoordinatorTuning.WORK_LEASE_TICKS,
                "Срок брони 600 тиков (30 с): дольше — цель занята без дела, короче — работа перераспределяется "
                        + "под руками"
                        + " Иначе тест workLease_DeclaredValue_MatchesTuningLiteral перестанет ловить эту поломку. При смене workLease_DeclaredValue_MatchesTuningLiteral проверь это утверждение.");
    }

    @Test
    void maxAssist_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                2,
                CoordinatorTuning.MAX_ASSIST,
                "Не больше двух помощников: больше — сектора пустеют, меньше — защитники гибнут"
                        + " Иначе тест maxAssist_DeclaredValue_MatchesTuningLiteral перестанет ловить эту поломку. При смене maxAssist_DeclaredValue_MatchesTuningLiteral проверь это утверждение.");
    }

    @Test
    void snapshotStale_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                40L,
                CoordinatorTuning.SNAPSHOT_STALE_TICKS,
                "Снимок просрочен через 40 тиков (2 с): выбывший дух не держит пост и цель вечно"
                        + " Иначе тест snapshotStale_DeclaredValue_MatchesTuningLiteral перестанет ловить эту поломку. При смене snapshotStale_DeclaredValue_MatchesTuningLiteral проверь это утверждение.");
    }

    @Test
    void snapshotGroupQueries_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                1,
                CoordinatorTuning.SNAPSHOT_GROUP_QUERIES,
                "Перечисление поселения — один запрос за пересборку: чаще — лишние обходы всех сущностей уровня"
                        + " Иначе тест snapshotGroupQueries_DeclaredValue_MatchesTuningLiteral перестанет ловить эту поломку. При смене snapshotGroupQueries_DeclaredValue_MatchesTuningLiteral проверь это утверждение.");
    }

    @Test
    void snapshotSpiritQueries_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                1,
                CoordinatorTuning.SNAPSHOT_SPIRIT_QUERIES,
                "Чтение духа — один запрос за пересборку: чаще — счётчик координатора растёт с духами, а не с периодом"
                        + " Иначе тест snapshotSpiritQueries_DeclaredValue_MatchesTuningLiteral перестанет ловить эту поломку. При смене snapshotSpiritQueries_DeclaredValue_MatchesTuningLiteral проверь это утверждение.");
    }

    @Test
    void everyConstant_AllValues_PositiveAndFinite() {
        assertTrue(
                CoordinatorTuning.SNAPSHOT_INTERVAL_TICKS > 0,
                "Интервал снимка положителен"
                        + " Иначе тест everyConstant_AllValues_PositiveAndFinite перестанет ловить эту поломку. При смене everyConstant_AllValues_PositiveAndFinite проверь это утверждение.");
        assertTrue(
                CoordinatorTuning.DEFENSE_RADIUS > 0 && Double.isFinite(CoordinatorTuning.DEFENSE_RADIUS),
                "Радиус обороны положителен и конечен"
                        + " Иначе тест everyConstant_AllValues_PositiveAndFinite перестанет ловить эту поломку. При смене everyConstant_AllValues_PositiveAndFinite проверь это утверждение.");
        assertTrue(
                CoordinatorTuning.DEFENSE_POST_RADIUS > 0 && Double.isFinite(CoordinatorTuning.DEFENSE_POST_RADIUS),
                "Радиус поста положителен и конечен"
                        + " Иначе тест everyConstant_AllValues_PositiveAndFinite перестанет ловить эту поломку. При смене everyConstant_AllValues_PositiveAndFinite проверь это утверждение.");
        assertTrue(
                CoordinatorTuning.WORK_LEASE_TICKS > 0,
                "Срок брони положителен"
                        + " Иначе тест everyConstant_AllValues_PositiveAndFinite перестанет ловить эту поломку. При смене everyConstant_AllValues_PositiveAndFinite проверь это утверждение.");
        assertTrue(
                CoordinatorTuning.MAX_ASSIST > 0,
                "Предел помощников положителен"
                        + " Иначе тест everyConstant_AllValues_PositiveAndFinite перестанет ловить эту поломку. При смене everyConstant_AllValues_PositiveAndFinite проверь это утверждение.");
        assertTrue(
                CoordinatorTuning.SNAPSHOT_STALE_TICKS > 0,
                "Просроченность снимка положительна"
                        + " Иначе тест everyConstant_AllValues_PositiveAndFinite перестанет ловить эту поломку. При смене everyConstant_AllValues_PositiveAndFinite проверь это утверждение.");
        assertTrue(
                CoordinatorTuning.SNAPSHOT_GROUP_QUERIES > 0,
                "Перечисление поселения делает запросы"
                        + " Иначе тест everyConstant_AllValues_PositiveAndFinite перестанет ловить эту поломку. При смене everyConstant_AllValues_PositiveAndFinite проверь это утверждение.");
        assertTrue(
                CoordinatorTuning.SNAPSHOT_SPIRIT_QUERIES > 0,
                "Чтение духа делает запросы"
                        + " Иначе тест everyConstant_AllValues_PositiveAndFinite перестанет ловить эту поломку. При смене everyConstant_AllValues_PositiveAndFinite проверь это утверждение.");
    }
}

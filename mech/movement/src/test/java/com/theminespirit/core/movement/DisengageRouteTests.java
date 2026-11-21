package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.api.Point;
import com.theminespirit.core.tactics.api.adapter.RetreatSpacing;
import org.junit.jupiter.api.Test;

class DisengageRouteTests {
    @Test
    void away_ThreatWestOfSelf_PlacesPointBehindSelfAtDistance() {
        Point self = new Point(2, 64, 0);
        Point threat = new Point(0, 64, 0);

        Point away = DisengageRoute.away(self, threat, 5);

        assertEquals(
                64.0,
                away.y(),
                0.001,
                "точка отхода остаётся на высоте духа: он отходит, а не взбирается"
                        + " Иначе тест away_ThreatWestOfSelf_PlacesPointBehindSelfAtDistance перестанет ловить эту поломку. При смене away_ThreatWestOfSelf_PlacesPointBehindSelfAtDistance проверь это утверждение.");
        assertEquals(
                5 + RetreatSpacing.HOME_ARRIVAL + DisengageRoute.ARRIVAL_SLACK,
                horizontal(threat, away),
                0.001,
                "точка отхода стоит за требуемой дистанцией с запасом на прибытие отхода: без запаса дух "
                        + "остановился бы ближе distance и не вышел из боя"
                        + " Иначе тест away_ThreatWestOfSelf_PlacesPointBehindSelfAtDistance перестанет ловить эту поломку. При смене away_ThreatWestOfSelf_PlacesPointBehindSelfAtDistance проверь это утверждение.");
        assertTrue(
                away.x() > self.x(),
                "точка отхода лежит дальше от угрозы, чем дух: он пятится, а не идёт на врага"
                        + " Иначе тест away_ThreatWestOfSelf_PlacesPointBehindSelfAtDistance перестанет ловить эту поломку. При смене away_ThreatWestOfSelf_PlacesPointBehindSelfAtDistance проверь это утверждение.");
    }

    @Test
    void away_SelfOnThreat_FallsBackToPositiveXWithoutNaN() {
        Point self = new Point(0, 64, 0);
        Point threat = new Point(0, 64, 0);

        Point away = DisengageRoute.away(self, threat, 5);

        assertEquals(
                5 + RetreatSpacing.HOME_ARRIVAL + DisengageRoute.ARRIVAL_SLACK,
                away.x(),
                0.001,
                "при совпадении позиций направление не определено: берём +X, иначе NaN увёл бы духа в никуда"
                        + " При смене away_SelfOnThreat_FallsBackToPositiveXWithoutNaN проверь это утверждение.");
        assertEquals(
                0,
                away.z(),
                0.001,
                "при совпадении позиций отход идёт строго по +X"
                        + " Иначе тест away_SelfOnThreat_FallsBackToPositiveXWithoutNaN перестанет ловить эту поломку. При смене away_SelfOnThreat_FallsBackToPositiveXWithoutNaN проверь это утверждение.");
    }

    @Test
    void away_SelfAlreadyBeyondDistance_KeepsOneBlockMargin() {
        Point self = new Point(10, 64, 0);
        Point threat = new Point(0, 64, 0);

        Point away = DisengageRoute.away(self, threat, 5);

        assertEquals(
                11,
                away.x(),
                0.001,
                "если дух уже за дистанцией, точка всё равно на шаг дальше: скрипт не должен разворачивать его к врагу"
                        + " Иначе тест away_SelfAlreadyBeyondDistance_KeepsOneBlockMargin перестанет ловить эту поломку. При смене away_SelfAlreadyBeyondDistance_KeepsOneBlockMargin проверь это утверждение.");
    }

    @Test
    void away_DistanceBelowMinimum_IsRejected() {
        Point self = new Point(2, 64, 0);
        Point threat = new Point(0, 64, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> DisengageRoute.away(self, threat, 0),
                "нулевая дистанция отхода бессмысленна: проверка обязана остановить расчёт до позиции NaN"
                        + " Иначе тест away_DistanceBelowMinimum_IsRejected перестанет ловить эту поломку. При смене away_DistanceBelowMinimum_IsRejected проверь это утверждение.");
    }

    @Test
    void reached_FartherThanDistance_IsTrue() {
        Point self = new Point(6, 64, 0);
        Point threat = new Point(0, 64, 0);

        assertTrue(
                DisengageRoute.reached(self, threat, 5),
                "расстояние 6 при требуемых 5 — цель отхода достигнута"
                        + " Иначе тест reached_FartherThanDistance_IsTrue перестанет ловить эту поломку. При смене reached_FartherThanDistance_IsTrue проверь это утверждение.");
    }

    @Test
    void reached_CloserThanDistance_IsFalse() {
        Point self = new Point(4, 64, 0);
        Point threat = new Point(0, 64, 0);

        assertFalse(
                DisengageRoute.reached(self, threat, 5),
                "расстояние 4 при требуемых 5 — дух ещё в бою, выходить рано"
                        + " Иначе тест reached_CloserThanDistance_IsFalse перестанет ловить эту поломку. При смене reached_CloserThanDistance_IsFalse проверь это утверждение.");
    }

    private static double horizontal(Point first, Point second) {
        return Math.hypot(first.x() - second.x(), first.z() - second.z());
    }
}

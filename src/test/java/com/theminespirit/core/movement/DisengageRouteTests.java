package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.Point;
import org.junit.jupiter.api.Test;

class DisengageRouteTests {
    @Test
    void away_ThreatWestOfSelf_PlacesPointBehindSelfAtDistance() {
        Point self = new Point(2, 64, 0);
        Point threat = new Point(0, 64, 0);

        Point away = DisengageRoute.away(self, threat, 5);

        assertEquals(64.0, away.y(), 0.001, "точка отхода остаётся на высоте духа: он отходит, а не взбирается");
        assertEquals(5, horizontal(threat, away), 0.001,
                "точка отхода стоит ровно в distance от угрозы: иначе дух либо не выйдет из боя, либо уйдёт далеко");
        assertTrue(away.x() > self.x(), "точка отхода лежит дальше от угрозы, чем дух: он пятится, а не идёт на врага");
    }

    @Test
    void away_SelfOnThreat_FallsBackToPositiveXWithoutNaN() {
        Point self = new Point(0, 64, 0);
        Point threat = new Point(0, 64, 0);

        Point away = DisengageRoute.away(self, threat, 5);

        assertEquals(5, away.x(), 0.001,
                "при совпадении позиций направление не определено: берём +X, иначе NaN увёл бы духа в никуда");
        assertEquals(0, away.z(), 0.001, "при совпадении позиций отход идёт строго по +X");
    }

    @Test
    void away_SelfAlreadyBeyondDistance_KeepsOneBlockMargin() {
        Point self = new Point(10, 64, 0);
        Point threat = new Point(0, 64, 0);

        Point away = DisengageRoute.away(self, threat, 5);

        assertEquals(11, away.x(), 0.001,
                "если дух уже за дистанцией, точка всё равно на шаг дальше: скрипт не должен разворачивать его к врагу");
    }

    @Test
    void away_DistanceBelowMinimum_IsRejected() {
        Point self = new Point(2, 64, 0);
        Point threat = new Point(0, 64, 0);

        assertThrows(IllegalArgumentException.class, () -> DisengageRoute.away(self, threat, 0),
                "нулевая дистанция отхода бессмысленна: проверка обязана остановить расчёт до позиции NaN");
    }

    @Test
    void reached_FartherThanDistance_IsTrue() {
        Point self = new Point(6, 64, 0);
        Point threat = new Point(0, 64, 0);

        assertTrue(DisengageRoute.reached(self, threat, 5),
                "расстояние 6 при требуемых 5 — цель отхода достигнута");
    }

    @Test
    void reached_CloserThanDistance_IsFalse() {
        Point self = new Point(4, 64, 0);
        Point threat = new Point(0, 64, 0);

        assertFalse(DisengageRoute.reached(self, threat, 5),
                "расстояние 4 при требуемых 5 — дух ещё в бою, выходить рано");
    }

    private static double horizontal(Point first, Point second) {
        return Math.hypot(first.x() - second.x(), first.z() - second.z());
    }
}

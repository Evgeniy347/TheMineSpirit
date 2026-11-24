package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TownMapWallTests {
    private static final TownRules RULES = TownRules.load();

    @Test
    void check_WallDiagonalLoop_NoFindings() {
        TownMap map = new TownMap(
                new TownMap.MapPoint(100, 100),
                List.of(),
                List.of(),
                List.of(
                        new TownMap.WallSegment(95, 90, 105, 90),
                        new TownMap.WallSegment(105, 90, 110, 95),
                        new TownMap.WallSegment(110, 95, 110, 105),
                        new TownMap.WallSegment(110, 105, 105, 110),
                        new TownMap.WallSegment(105, 110, 95, 110),
                        new TownMap.WallSegment(95, 110, 90, 105),
                        new TownMap.WallSegment(90, 105, 90, 95),
                        new TownMap.WallSegment(90, 95, 95, 90)));

        List<TownMapFinding> findings =
                TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER);

        assertEquals(
                List.of(),
                findings,
                "Замкнутая стена с диагональными отрезками 45° проходит проверку: иначе огибание воды под 45° "
                        + "невозможно; получено " + findings
                        + " Иначе тест check_WallDiagonalLoop_NoFindings перестанет ловить эту поломку. При смене check_WallDiagonalLoop_NoFindings проверь это утверждение.");
    }

    @Test
    void check_WallObliqueSegment_Reported() {
        TownMap map = new TownMap(
                new TownMap.MapPoint(100, 100),
                List.of(),
                List.of(),
                List.of(
                        new TownMap.WallSegment(95, 90, 105, 90),
                        new TownMap.WallSegment(105, 90, 110, 95),
                        new TownMap.WallSegment(110, 95, 110, 105),
                        new TownMap.WallSegment(110, 105, 105, 110),
                        new TownMap.WallSegment(105, 110, 95, 110),
                        new TownMap.WallSegment(95, 110, 90, 105),
                        new TownMap.WallSegment(90, 105, 90, 95),
                        new TownMap.WallSegment(90, 95, 95, 90),
                        new TownMap.WallSegment(95, 90, 98, 92)));

        List<TownMapFinding> findings =
                TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER);

        assertTrue(
                findings.stream()
                        .anyMatch(finding -> "segment".equals(finding.kind()) && "wall".equals(finding.object())),
                "Отрезок не под 45° отклоняется: косую стену нечем класть; получено " + findings
                        + " Иначе тест check_WallObliqueSegment_Reported перестанет ловить эту поломку. При смене check_WallObliqueSegment_Reported проверь это утверждение.");
    }

    @Test
    void check_ConnectedStreets_NoFindings() {
        TownMap map = new TownMap(
                new TownMap.MapPoint(100, 100),
                List.of(),
                List.of(
                        new TownMap.MapStreet("main_ns", TownMapRules.MAIN, 95, 0, 101, 199),
                        new TownMap.MapStreet("side_ew", TownMapRules.SIDE, 0, 95, 199, 101)),
                List.of());

        List<TownMapFinding> findings =
                TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER);

        assertEquals(
                List.of(),
                findings,
                "Пересекающиеся улицы соединены и проходят проверку: иначе нельзя собрать город с сеткой улиц; "
                        + "получено " + findings
                        + " Иначе тест check_ConnectedStreets_NoFindings перестанет ловить эту поломку. При смене check_ConnectedStreets_NoFindings проверь это утверждение.");
    }

    @Test
    void check_DisconnectedStreet_Reported() {
        TownMap map = new TownMap(
                new TownMap.MapPoint(100, 100),
                List.of(),
                List.of(
                        new TownMap.MapStreet("main_ns", TownMapRules.MAIN, 95, 0, 101, 199),
                        new TownMap.MapStreet("lost_ew", TownMapRules.SIDE, 0, 150, 60, 156)),
                List.of());

        List<TownMapFinding> findings =
                TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER);

        assertTrue(
                findings.stream()
                        .anyMatch(
                                finding -> "disconnected".equals(finding.kind()) && "lost_ew".equals(finding.object())),
                "Улица без соединения с остальными отклоняется: иначе в городе остаётся дорога в никуда; получено "
                        + findings
                        + " Иначе тест check_DisconnectedStreet_Reported перестанет ловить эту поломку. При смене check_DisconnectedStreet_Reported проверь это утверждение.");
    }
}

package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TownMapCheckTests {
    private static final TownRules RULES = TownRules.load();

    @Test
    void check_GoodMap_NoFindings() {
        List<TownMapFinding> findings = TownMapCheck.validate(
                TownMapFixtures.withoutRing(), RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER);

        assertEquals(
                List.of(),
                findings,
                "Ровная карта проходит проверку без замечаний: загрузка обязана быть тихой, иначе ложные "
                        + "ошибки заставляют стратега править здоровый план; получено " + findings
                        + " При смене check_GoodMap_NoFindings проверь это утверждение.");
    }

    @Test
    void check_OverlappingPlots_NamesBothIds() {
        TownMap map = TownMapFixtures.withoutRing();
        TownMap moved = new TownMap(
                map.anchor(),
                List.of(map.plots().get(0), new TownMap.MapPlot("hut", "hut", 20, 20, 35, 35, "north")),
                map.streets(),
                map.wall());

        List<TownMapFinding> findings =
                TownMapCheck.validate(moved, RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER);

        assertTrue(
                findings.stream()
                        .anyMatch(finding -> "overlap".equals(finding.kind())
                                && finding.message().contains("hut")
                                && finding.message().contains("family_house_a")),
                "Наложение участков называется обоими id: стратег видит, кого двигать, и не перебирает "
                        + "всю карту; получено " + findings
                        + " Иначе тест check_OverlappingPlots_NamesBothIds перестанет ловить эту поломку. При смене check_OverlappingPlots_NamesBothIds проверь это утверждение.");
    }

    @Test
    void check_PlotInWater_NamesThePlot() {
        ColumnMask water = ColumnMask.rect(20, 20, 30, 30);
        List<TownMapFinding> findings =
                TownMapCheck.validate(TownMapFixtures.withoutRing(), RULES, TownMapFixtures.REGION, water);

        assertTrue(
                findings.stream()
                        .anyMatch(
                                finding -> "water".equals(finding.kind()) && "family_house_a".equals(finding.object())),
                "Участок в воде отклоняется с именем участка: иначе дом встанет на дно, а стратег будет "
                        + "искать причину по всей карте; получено " + findings
                        + " Иначе тест check_PlotInWater_NamesThePlot перестанет ловить эту поломку. При смене check_PlotInWater_NamesThePlot проверь это утверждение.");
    }

    @Test
    void check_NarrowStreet_IsRejected() {
        TownMap map = TownMapFixtures.withoutRing();
        TownMap narrow = new TownMap(
                map.anchor(),
                map.plots(),
                List.of(new TownMap.MapStreet("main_ns", TownMapRules.MAIN, 95, 2, 97, 197)),
                map.wall());

        List<TownMapFinding> findings =
                TownMapCheck.validate(narrow, RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER);

        assertTrue(
                findings.stream()
                        .anyMatch(finding -> "narrow".equals(finding.kind()) && "main_ns".equals(finding.object())),
                "Улица уже пяти блоков отклоняется: духи и обозы ходят по проезду, а три блока — тропа "
                        + "между домами; получено " + findings
                        + " Иначе тест check_NarrowStreet_IsRejected перестанет ловить эту поломку. При смене check_NarrowStreet_IsRejected проверь это утверждение.");
    }

    @Test
    void check_WallWithHole_IsRejected() {
        TownMap map = TownMapFixtures.withoutRing();
        TownMap broken =
                new TownMap(map.anchor(), map.plots(), map.streets(), map.wall().subList(0, 3));

        List<TownMapFinding> findings =
                TownMapCheck.validate(broken, RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER);

        assertTrue(
                findings.stream().anyMatch(finding -> "loop".equals(finding.kind()) && "wall".equals(finding.object())),
                "Незамкнутая стена отклоняется: иначе поселение остаётся с дырой, а кладка идёт к разрыву; "
                        + "получено " + findings
                        + " Иначе тест check_WallWithHole_IsRejected перестанет ловить эту поломку. При смене check_WallWithHole_IsRejected проверь это утверждение.");
    }

    @Test
    void check_PlotOnStreet_IsRejected() {
        TownMap map = TownMapFixtures.withoutRing();
        TownMap moved = new TownMap(
                map.anchor(),
                List.of(new TownMap.MapPlot("hut", "hut", 90, 20, 105, 35, "north")),
                map.streets(),
                map.wall());

        List<TownMapFinding> findings =
                TownMapCheck.validate(moved, RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER);

        assertTrue(
                findings.stream()
                        .anyMatch(finding -> "street".equals(finding.kind()) && "hut".equals(finding.object())),
                "Участок на улице отклоняется: иначе лот перекрывает проезд и улица упирается в стену дома; "
                        + "получено " + findings
                        + " Иначе тест check_PlotOnStreet_IsRejected перестанет ловить эту поломку. При смене check_PlotOnStreet_IsRejected проверь это утверждение.");
    }
}

package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TownMapCheckTests {
    private static final TownRules RULES = TownRules.load();

    @Test
    void check_NewPlotOverOld_Reported() {
        TownMap base = TownMapFixtures.small();
        TownMap previous = new TownMap(
                base.anchor(),
                List.of(new TownMap.MapPlot("old_hut", "hut", 60, 60, 75, 75, "north")),
                base.streets(),
                base.wall());
        TownMap map = new TownMap(
                base.anchor(),
                List.of(new TownMap.MapPlot("new_hut", "hut", 60, 60, 75, 75, "north")),
                base.streets(),
                base.wall());

        List<TownMapFinding> findings =
                TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER, previous);

        assertTrue(
                findings.stream()
                        .anyMatch(finding -> "occupied".equals(finding.kind()) && "new_hut".equals(finding.object())),
                "Новый участок поверх прежнего обязан отклоняться с причиной: иначе план применяется, а старый дом "
                        + "стоит и духи не могут его разобрать; получено " + findings
                        + " Иначе тест check_NewPlotOverOld_Reported перестанет ловить эту поломку. При смене "
                        + "check_NewPlotOverOld_Reported проверь вид occupied");
    }

    @Test
    void check_GoodMap_NoFindings() {
        List<TownMapFinding> findings =
                TownMapCheck.validate(TownMapFixtures.small(), RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER);

        assertEquals(
                List.of(),
                findings,
                "Ровная карта проходит проверку без замечаний: загрузка обязана быть тихой, иначе ложные "
                        + "ошибки заставляют стратега править здоровый план; получено " + findings
                        + " При смене check_GoodMap_NoFindings проверь это утверждение.");
    }

    @Test
    void check_InheritedPlotInWater_Allowed() {
        TownMap map = TownMapFixtures.small();
        ColumnMask water = ColumnMask.rect(10, 10, 20, 20);

        List<TownMapFinding> findings = TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, water, map);

        assertEquals(
                List.of(),
                findings,
                "Участок, переехавший из старого плана клетка в клетку, не блокирует применение карты: иначе "
                        + "живое поселение нельзя править из-за воды под уже стоящей постройкой; получено " + findings
                        + " Иначе тест check_InheritedPlotInWater_Allowed перестанет ловить эту поломку. При смене check_InheritedPlotInWater_Allowed проверь это утверждение.");
    }

    @Test
    void check_MovedPlotIntoWater_Reported() {
        TownMap map = TownMapFixtures.small();
        TownMap previous = new TownMap(
                map.anchor(),
                List.of(map.plots().get(0), new TownMap.MapPlot("hut", "hut", 60, 60, 75, 75, "north")),
                map.streets(),
                map.wall());
        ColumnMask water = ColumnMask.rect(12, 62, 22, 72);

        List<TownMapFinding> findings = TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, water, previous);

        assertTrue(
                findings.stream().anyMatch(finding -> "water".equals(finding.kind()) && "hut".equals(finding.object())),
                "Переехавший в воду участок обязан называться: унаследованное послабление не прячет правку; "
                        + "получено " + findings
                        + " Иначе тест check_MovedPlotIntoWater_Reported перестанет ловить эту поломку. При смене check_MovedPlotIntoWater_Reported проверь это утверждение.");
    }

    @Test
    void check_InheritedNarrowStreet_Allowed() {
        TownMap base = TownMapFixtures.small();
        List<TownMap.MapStreet> streets = new ArrayList<>(base.streets());
        streets.add(new TownMap.MapStreet("thin", TownMapRules.SIDE, 0, 0, 0, 49));
        TownMap map = new TownMap(base.anchor(), base.plots(), streets, base.wall());

        List<TownMapFinding> findings =
                TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER, map);

        assertEquals(
                List.of(),
                findings,
                "Старая узкая полоса улицы не мешает применить карту без правки: иначе любая правка упирается "
                        + "в геометрию миграции; получено " + findings
                        + " Иначе тест check_InheritedNarrowStreet_Allowed перестанет ловить эту поломку. При смене check_InheritedNarrowStreet_Allowed проверь это утверждение.");
    }

    @Test
    void check_NewNarrowStreet_Reported() {
        TownMap base = TownMapFixtures.small();
        List<TownMap.MapStreet> streets = new ArrayList<>(base.streets());
        streets.add(new TownMap.MapStreet("thin", TownMapRules.SIDE, 0, 0, 0, 49));
        TownMap map = new TownMap(base.anchor(), base.plots(), streets, base.wall());

        List<TownMapFinding> findings =
                TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER, base);

        assertTrue(
                findings.stream()
                        .anyMatch(finding -> "narrow".equals(finding.kind()) && "thin".equals(finding.object())),
                "Новая улица уже 5 блоков отклоняется: послабление только для унаследованных полос; получено "
                        + findings
                        + " Иначе тест check_NewNarrowStreet_Reported перестанет ловить эту поломку. При смене check_NewNarrowStreet_Reported проверь это утверждение.");
    }

    @Test
    void check_OverlappingPlots_NamesBothIds() {
        TownMap map = TownMapFixtures.small();
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
                TownMapCheck.validate(TownMapFixtures.small(), RULES, TownMapFixtures.REGION, water);

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
        TownMap map = TownMapFixtures.small();
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
        TownMap map = TownMapFixtures.small();
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
        TownMap map = TownMapFixtures.small();
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

    @Test
    void check_RotatedField_SizeReported() {
        TownMap base = TownMapFixtures.small();
        TownMap map = new TownMap(
                base.anchor(),
                List.of(new TownMap.MapPlot("field_a", "field_a", 10, 10, 73, 41, "north")),
                base.streets(),
                base.wall());

        List<TownMapFinding> findings =
                TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER);

        assertTrue(
                findings.stream()
                        .anyMatch(finding -> "size".equals(finding.kind()) && "field_a".equals(finding.object())),
                "Пашня с поворотом отклоняется: в каталоге у неё rotate=false, схему поворачивать нельзя; получено "
                        + findings
                        + " Иначе тест check_RotatedField_SizeReported перестанет ловить эту поломку. При смене check_RotatedField_SizeReported проверь это утверждение.");
    }

    @Test
    void check_WallNearPlot_ClearanceReported() {
        TownMap base = TownMapFixtures.small();
        TownMap map = new TownMap(
                base.anchor(),
                List.of(new TownMap.MapPlot("hut", "hut", 1, 1, 16, 16, "north")),
                base.streets(),
                base.wall());

        List<TownMapFinding> findings =
                TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, TownMapFixtures.NO_WATER);

        assertTrue(
                findings.stream()
                        .anyMatch(finding -> "clearance".equals(finding.kind()) && "wall".equals(finding.object())),
                "Стена ближе двух блоков к участку отклоняется: иначе кладка режет двор и проход к двери; получено "
                        + findings
                        + " Иначе тест check_WallNearPlot_ClearanceReported перестанет ловить эту поломку. При смене check_WallNearPlot_ClearanceReported проверь это утверждение.");
    }
}

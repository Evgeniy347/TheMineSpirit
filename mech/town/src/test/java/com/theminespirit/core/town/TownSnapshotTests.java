package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TownSnapshotTests {
    private static final TownPlan PLAN = TownFixtures.flatPlan();
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void of_PlanWithProgress_LotsLabelledWithPercent() {
        TownSnapshot snapshot = TownSnapshot.of(
                PLAN,
                SITE,
                Map.of(StageSites.HUT, new TownSnapshot.Progress(75, 100, true)),
                List.of(List.of(new BlockCoord(1, 2, 3))));

        assertEquals(
                PLAN.placements().size(),
                snapshot.lots().size(),
                "Карта показывает каждое место плана: постройка без прямоугольника пропадёт с экрана J"
                        + " Иначе тест of_PlanWithProgress_LotsLabelledWithPercent перестанет ловить эту поломку. При смене of_PlanWithProgress_LotsLabelledWithPercent проверь это утверждение.");
        TownSnapshot.Lot hut = snapshot.lots().stream()
                .filter(lot -> StageSites.HUT.equals(lot.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(
                List.of("Хижина", 75, true),
                List.of(hut.name(), hut.progress().percent(), hut.progress().damaged()),
                "Подпись и готовность хижины — из каталога и аудита: 75 из 100 клеток, повреждена — к восстановлению"
                        + " Иначе тест of_PlanWithProgress_LotsLabelledWithPercent перестанет ловить эту поломку. При смене of_PlanWithProgress_LotsLabelledWithPercent проверь это утверждение.");
        assertEquals(
                1,
                snapshot.paths().size(),
                "Сеть тропинок передаётся в снимок как есть"
                        + " Иначе тест of_PlanWithProgress_LotsLabelledWithPercent перестанет ловить эту поломку. При смене of_PlanWithProgress_LotsLabelledWithPercent проверь это утверждение.");
    }

    @Test
    void of_ActualStage_ShowsItInsteadOfSiteStage() {
        TownSnapshot snapshot = TownSnapshot.of(PLAN, SITE, 2, Map.of(), List.of());

        assertEquals(
                1,
                SITE.stage(),
                "Место этапа остаётся первым: по нему меряется готовность этапа 1"
                        + " Иначе тест of_ActualStage_ShowsItInsteadOfSiteStage перестанет ловить эту поломку. При смене of_ActualStage_ShowsItInsteadOfSiteStage проверь это утверждение.");
        assertEquals(
                2,
                snapshot.stage(),
                "Снимок показывает действительный этап поселения, а не этап места постройки: spirit plan "
                        + "не печатает «этап 1» вечно"
                        + " Иначе тест of_ActualStage_ShowsItInsteadOfSiteStage перестанет ловить эту поломку. При смене of_ActualStage_ShowsItInsteadOfSiteStage проверь это утверждение.");
        assertTrue(
                snapshot.lots().stream().anyMatch(lot -> lot.stage() == 2),
                "Снимок действительного этапа перечисляет его постройки: иначе показывать этап 2 нечем"
                        + " При смене of_ActualStage_ShowsItInsteadOfSiteStage проверь это утверждение.");
        assertTrue(
                PLAN.placements().stream().anyMatch(place -> place.stage() == 2),
                "В плане есть постройки этапа 2: снимок берёт их из плана, а не из места этапа"
                        + " Иначе тест of_ActualStage_ShowsItInsteadOfSiteStage перестанет ловить эту поломку. При смене of_ActualStage_ShowsItInsteadOfSiteStage проверь это утверждение.");
    }

    @Test
    void of_Plan_ContourAndStreetsComplete() {
        TownSnapshot snapshot = TownSnapshot.of(PLAN, SITE, Map.of(), List.of());

        int streetCells = snapshot.streets().stream()
                .mapToInt(street -> (street.x2() - street.x1() + 1) * (street.z2() - street.z1() + 1))
                .sum();
        int planned = PLAN.streets().main().count()
                + PLAN.streets().side().count()
                + PLAN.streets().ring().count();
        assertEquals(
                planned,
                streetCells,
                "Полосы улиц снимка покрывают ровно клетки улиц плана: иначе карта рисует улицу там, где её нет"
                        + " При смене of_Plan_ContourAndStreetsComplete проверь это утверждение.");
        assertEquals(
                PLAN.fence().posts(),
                snapshot.contour(),
                "Контур ограды на карте — линия столбов плана"
                        + " Иначе тест of_Plan_ContourAndStreetsComplete перестанет ловить эту поломку. При смене of_Plan_ContourAndStreetsComplete проверь это утверждение.");
        assertTrue(
                !snapshot.stageFence().isEmpty() && !snapshot.stageGates().isEmpty(),
                "Ограда этапа с калитками видна на карте отдельно от контура участка"
                        + " Иначе тест of_Plan_ContourAndStreetsComplete перестанет ловить эту поломку. При смене of_Plan_ContourAndStreetsComplete проверь это утверждение.");
    }
}

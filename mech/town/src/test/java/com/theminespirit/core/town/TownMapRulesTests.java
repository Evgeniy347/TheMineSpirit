package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class TownMapRulesTests {
    private static final TownRules RULES = TownRules.load();
    private static final BlockCoord ANCHOR = new BlockCoord(100, 64, 100);

    @Test
    void build_Map_KeepsPlotSizeStageAndDoor() {
        TownPlan plan =
                TownMapRules.build(TownMapFixtures.withoutRing(), RULES, TownMapFixtures.REGION, ANCHOR, building -> 2);

        Placement hut = plan.placement("hut").orElseThrow();
        assertEquals(
                16,
                hut.width(),
                "Ширина участка берётся из каталога town.json, а не из картинки: иначе схема и участок "
                        + "разойдутся, и дом вылезет за лот"
                        + " Иначе тест build_Map_KeepsPlotSizeStageAndDoor перестанет ловить эту поломку. При смене build_Map_KeepsPlotSizeStageAndDoor проверь это утверждение.");
        assertEquals(
                Placement.Facing.NORTH,
                hut.facing(),
                "Сторона двери переживает загрузку: от неё поворот схемы и метка на PNG; потеря поворота "
                        + "разворачивает дом спиной к улице"
                        + " Иначе тест build_Map_KeepsPlotSizeStageAndDoor перестанет ловить эту поломку. При смене build_Map_KeepsPlotSizeStageAndDoor проверь это утверждение.");
        assertEquals(
                2,
                hut.stage(),
                "Этап постройки приходит из этапов, а не из карты: задания доски решают, когда ставить дом; "
                        + "нулевой этап навсегда оставил бы дом в очереди"
                        + " Иначе тест build_Map_KeepsPlotSizeStageAndDoor перестанет ловить эту поломку. При смене build_Map_KeepsPlotSizeStageAndDoor проверь это утверждение.");
    }

    @Test
    void build_StreetThroughWall_MakesOpening() {
        TownPlan plan =
                TownMapRules.build(TownMapFixtures.withoutRing(), RULES, TownMapFixtures.REGION, ANCHOR, building -> 1);

        assertFalse(
                plan.fence().gates().isEmpty(),
                "Улица, упирающаяся в стену, оставляет проём: проход насквозь без ворот — единственный вход; "
                        + "без проёма главная улица упирается в глухую кладку"
                        + " Иначе тест build_StreetThroughWall_MakesOpening перестанет ловить эту поломку. При смене build_StreetThroughWall_MakesOpening проверь это утверждение.");
        int gate = plan.fence().gates().get(0);
        assertTrue(
                plan.streets()
                        .main()
                        .contains(
                                plan.fence().posts().get(gate).x(),
                                plan.fence().posts().get(gate).z()),
                "Проём стоит на клетке улицы: кладка пропускает проезд, а не соседнюю клетку; "
                        + "иначе духи бьются в стену рядом с проходом"
                        + " Иначе тест build_StreetThroughWall_MakesOpening перестанет ловить эту поломку. При смене build_StreetThroughWall_MakesOpening проверь это утверждение.");
    }

    @Test
    void build_RingKind_DerivesBandInsideWall() {
        TownPlan plan =
                TownMapRules.build(TownMapFixtures.small(), RULES, TownMapFixtures.REGION, ANCHOR, building -> 1);

        assertFalse(
                plan.streets().ring().isEmpty(),
                "Кольцевая улица вида ring размечается полосой от границы: координат у неё нет; пустая полоса "
                        + "означает, что обход стены потерян"
                        + " Иначе тест build_RingKind_DerivesBandInsideWall перестанет ловить эту поломку. При смене build_RingKind_DerivesBandInsideWall проверь это утверждение.");
        assertTrue(
                plan.streets().ring().contains(4, 100),
                "Полоса кольца лежит в L-окрестности границы (отступ 2, ширина 5): клетка (4,100) обязана "
                        + "быть кольцом; иначе полоса сдвинута и упирается в дома"
                        + " Иначе тест build_RingKind_DerivesBandInsideWall перестанет ловить эту поломку. При смене build_RingKind_DerivesBandInsideWall проверь это утверждение.");
        assertFalse(
                plan.streets().ring().contains(50, 100),
                "В середине участка кольца нет: полоса шириной 5 не заливает весь город; "
                        + "иначе кольцо съест место под дома"
                        + " Иначе тест build_RingKind_DerivesBandInsideWall перестанет ловить эту поломку. При смене build_RingKind_DerivesBandInsideWall проверь это утверждение.");
    }
}

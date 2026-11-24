package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.api.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.OrchardPlot;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownFixtures;
import org.junit.jupiter.api.Test;

class PlotPlanBlocksTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void plan_SameSite_IsReusedInsteadOfRebuilt() {
        PlotPlanBlocks.Plan first = PlotPlanBlocks.of(SITE);
        PlotPlanBlocks.Plan second = PlotPlanBlocks.of(SITE);

        assertFalse(
                first.blocks().isEmpty(),
                "План этапа обязан содержать блоки дома и двора: пустой план слеп"
                        + " Иначе тест plan_SameSite_IsReusedInsteadOfRebuilt перестанет ловить эту поломку. При смене plan_SameSite_IsReusedInsteadOfRebuilt проверь это утверждение.");
        assertSame(
                first,
                second,
                "План одного и того же этапа не пересобирается на каждый запрос: раскладка ограды — дорогой поиск "
                        + "по клеткам, и повтор на каждом шаге сканирования держал серверный поток сотни мс"
                        + " Иначе тест plan_SameSite_IsReusedInsteadOfRebuilt перестанет ловить эту поломку. При смене plan_SameSite_IsReusedInsteadOfRebuilt проверь это утверждение.");
    }

    @Test
    void plan_OtherSite_IsRebuilt() {
        PlotPlanBlocks.Plan first = PlotPlanBlocks.of(SITE);
        PlotPlanBlocks.Plan other = PlotPlanBlocks.of(TownFixtures.site(TownFixtures.hillyPlan(42)));

        assertNotSame(
                first,
                other,
                "План другого этапа/участка собирается заново: кэш не подменяет постройки чужого места"
                        + " Иначе тест plan_OtherSite_IsRebuilt перестанет ловить эту поломку. При смене plan_OtherSite_IsRebuilt проверь это утверждение.");
    }

    @Test
    void plan_ForeignSettlementWood_MainOrchardStaysOak() {
        StageSite birch = TownFixtures.flatSite("birch");
        PlotPlanBlocks.Plan plan = PlotPlanBlocks.of(birch);

        assertEquals(
                birch.orchardSaplings().size(),
                plan.expected("minecraft:oak_sapling").size(),
                "Главный сад — яблоневый (дуб): порода поселения birch не подменяет его саженцы"
                        + " Иначе тест plan_ForeignSettlementWood_MainOrchardStaysOak перестанет ловить эту поломку. При смене plan_ForeignSettlementWood_MainOrchardStaysOak проверь это утверждение.");
        assertTrue(
                plan.expected("minecraft:birch_sapling").isEmpty(),
                "Чужая порода поселения не оставляет в главном саду birch-саженцев"
                        + " Иначе тест plan_ForeignSettlementWood_MainOrchardStaysOak перестанет ловить эту поломку. При смене plan_ForeignSettlementWood_MainOrchardStaysOak проверь это утверждение.");
        assertEquals(
                "birch",
                birch.wood(),
                "Порода поселения остаётся birch для остальных построек и ограды"
                        + " Иначе тест plan_ForeignSettlementWood_MainOrchardStaysOak перестанет ловить эту поломку. При смене plan_ForeignSettlementWood_MainOrchardStaysOak проверь это утверждение.");
    }

    @Test
    void plan_ExtraOrchardTrees_CountAsPlannedPlantings() {
        StageSite site = TownFixtures.site(TownFixtures.planWithOrchard("orchard_jungle"));
        OrchardPlot plot = site.extraOrchards().stream()
                .filter(found -> "orchard_jungle".equals(found.building()))
                .findFirst()
                .orElseThrow();
        PlotPlanBlocks.Plan plan = PlotPlanBlocks.of(site);

        for (BlockCoord at : plot.saplings()) {
            assertTrue(
                    plan.planned(at),
                    "Саженец сада '" + plot.building() + "' в " + at + " вне плана поселения: уборка считает блок "
                            + "лишним и рубит дерево на месте схемы"
                            + " Иначе тест plan_ExtraOrchardTrees_CountAsPlannedPlantings перестанет ловить эту поломку. При смене plan_ExtraOrchardTrees_CountAsPlannedPlantings проверь это утверждение.");
            assertEquals(
                    WoodSpecies.swap("minecraft:oak_sapling", plot.wood()),
                    plan.blocks().get(at),
                    "В плане сад '" + plot.building() + "' ждёт саженец своей породы, иначе аудит не узнаёт посадку"
                            + " При смене plan_ExtraOrchardTrees_CountAsPlannedPlantings проверь это утверждение.");
        }
    }
}

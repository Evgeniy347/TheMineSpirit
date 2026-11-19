package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.api.PlanBuilding;
import org.junit.jupiter.api.Test;

class PlotDemolishScopeTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void demolish_HouseCellWithOwnBuilding_Allowed() {
        BlockCoord cell = SITE.houseOrigin();

        assertInstanceOf(
                PlotProtection.Allowed.class,
                PlotProtection.check(
                        SITE,
                        cell,
                        PlotProtection.Action.BREAK,
                        PlotProtection.Ground.PLAN_DEMOLISH,
                        PlanBuilding.HOUSE),
                "Задача сноса одной клетки дома называет постройку этой клетки (World: BuildPlanOwner.demolished): "
                        + "клетка дома при собственном имени снимается, и фильтр скана не имеет права её выкидывать");
    }

    @Test
    void demolish_HouseCellWithoutOwnBuilding_Refused() {
        BlockCoord cell = SITE.houseOrigin();

        assertInstanceOf(
                PlotProtection.Refused.class,
                PlotProtection.check(
                        SITE,
                        cell,
                        PlotProtection.Action.BREAK,
                        PlotProtection.Ground.PLAN_DEMOLISH,
                        PlanBuilding.NONE),
                "Без имени постройки клетка дома защищена: это правило запрещает чужой разбор, "
                        + "и ровно оно же отказывало клеткам дома в живом журнале 17.11");
    }
}

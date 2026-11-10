package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownFixtures;
import org.junit.jupiter.api.Test;

class PlotPlanBlocksTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void plan_SameSite_IsReusedInsteadOfRebuilt() {
        PlotPlanBlocks.Plan first = PlotPlanBlocks.of(SITE);
        PlotPlanBlocks.Plan second = PlotPlanBlocks.of(SITE);

        assertFalse(first.blocks().isEmpty(), "План этапа обязан содержать блоки дома и ограды: пустой план слеп");
        assertSame(
                first,
                second,
                "План одного и того же этапа не пересобирается на каждый запрос: раскладка ограды — дорогой поиск "
                        + "по клеткам, и повтор на каждом шаге сканирования держал серверный поток сотни мс");
    }

    @Test
    void plan_OtherSite_IsRebuilt() {
        PlotPlanBlocks.Plan first = PlotPlanBlocks.of(SITE);
        PlotPlanBlocks.Plan other = PlotPlanBlocks.of(TownFixtures.site(TownFixtures.hillyPlan(42)));

        assertNotSame(
                first, other, "План другого этапа/участка собирается заново: кэш не подменяет постройки чужого места");
    }
}

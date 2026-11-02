package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StageRules;
import java.util.List;
import org.junit.jupiter.api.Test;

class TownPlanOriginTests {
    private static final TownRules RULES = TownRules.load();
    private static final List<List<String>> STAGES = StageRules.load().plans();

    @Test
    void keepStored_OldProbeRadius_KeepsPlanOrigins() {
        TownPlan base = TownPlannerTests.plan((x, z) -> 70, (x, z) -> 0);
        TownPlan old = new TownPlan(
                base.anchor(),
                ColumnMask.rect(
                        base.anchor().x(),
                        base.anchor().z(),
                        base.anchor().x() + 20,
                        base.anchor().z() + 20),
                base.streets(),
                base.placements(),
                base.fence(),
                base.unplaced());

        assertFalse(TownPlanner.laidWith(old, RULES), "Старый обмер не совпадает с текущим радиусом рельефа");
        assertTrue(
                TownPlanner.keepStored(old),
                "План прежнего обмера сохраняют: иначе рестарт переносит origin уже построенных мест");
    }

    @Test
    void plant_Extension_KeepsExistingPlotOrigins() {
        TownPlan base = TownPlannerTests.plan((x, z) -> 70, (x, z) -> 0);
        Placement before = base.placement(StageSites.ORCHARD).orElseThrow();

        TownPlan grown = TownPlanner.plant(
                TownPlannerTests.grid((x, z) -> 70, (x, z) -> 0),
                base,
                RULES,
                STAGES,
                List.of(OrchardSites.siteId("orchard_jungle")),
                1);
        Placement after = grown.placement(StageSites.ORCHARD).orElseThrow();

        assertEquals(
                before,
                after,
                "Рост плана ради сада другой породы не переносит уже размеченный сад: одна постройка — один origin");
    }
}

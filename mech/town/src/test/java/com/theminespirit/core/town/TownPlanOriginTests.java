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
    void atLevel_KeepsLayout_WritesOneHeightIntoPlan() {
        TownPlan base = TownPlannerTests.plan((x, z) -> 70, (x, z) -> 0);

        TownPlan leveled = base.atLevel(63);

        assertEquals(
                63,
                leveled.anchor().y(),
                "Высота поселения — единственная высота плана после основания"
                        + " Иначе тест atLevel_KeepsLayout_WritesOneHeightIntoPlan перестанет ловить эту поломку. При смене atLevel_KeepsLayout_WritesOneHeightIntoPlan проверь это утверждение.");
        assertEquals(
                base.anchor().x(),
                leveled.anchor().x(),
                "Перенос высоты не двигает план по x"
                        + " Иначе тест atLevel_KeepsLayout_WritesOneHeightIntoPlan перестанет ловить эту поломку. При смене atLevel_KeepsLayout_WritesOneHeightIntoPlan проверь это утверждение.");
        assertEquals(
                base.anchor().z(),
                leveled.anchor().z(),
                "Перенос высоты не двигает план по z"
                        + " Иначе тест atLevel_KeepsLayout_WritesOneHeightIntoPlan перестанет ловить эту поломку. При смене atLevel_KeepsLayout_WritesOneHeightIntoPlan проверь это утверждение.");
        assertEquals(
                base.region(),
                leveled.region(),
                "Контур плана при смене высоты не меняется"
                        + " Иначе тест atLevel_KeepsLayout_WritesOneHeightIntoPlan перестанет ловить эту поломку. При смене atLevel_KeepsLayout_WritesOneHeightIntoPlan проверь это утверждение.");
        assertEquals(
                base.placements(),
                leveled.placements(),
                "Места построек при смене высоты не меняются"
                        + " Иначе тест atLevel_KeepsLayout_WritesOneHeightIntoPlan перестанет ловить эту поломку. При смене atLevel_KeepsLayout_WritesOneHeightIntoPlan проверь это утверждение.");
    }

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

        assertFalse(
                TownPlanner.laidWith(old, RULES),
                "Старый обмер не совпадает с текущим радиусом рельефа"
                        + " Иначе тест keepStored_OldProbeRadius_KeepsPlanOrigins перестанет ловить эту поломку. При смене keepStored_OldProbeRadius_KeepsPlanOrigins проверь это утверждение.");
        assertTrue(
                TownPlanner.keepStored(old),
                "План прежнего обмера сохраняют: иначе рестарт переносит origin уже построенных мест"
                        + " При смене keepStored_OldProbeRadius_KeepsPlanOrigins проверь это утверждение.");
    }

    @Test
    void plant_Extension_KeepsExistingPlotOrigins() {
        TownPlan base = TownPlannerTests.plan((x, z) -> 70, (x, z) -> 0);
        Placement before = base.placement(StageSites.ORCHARD).orElseThrow();

        TownPlan grown = TownPlanner.plant(
                TownPlannerTests.grid((x, z) -> 70, (x, z) -> 0), base, RULES, STAGES, List.of("orchard_jungle"), 1);
        Placement after = grown.placement(StageSites.ORCHARD).orElseThrow();

        assertEquals(
                before,
                after,
                "Рост плана ради сада другой породы не переносит уже размеченный сад: одна постройка — один origin"
                        + " Иначе тест plant_Extension_KeepsExistingPlotOrigins перестанет ловить эту поломку. При смене plant_Extension_KeepsExistingPlotOrigins проверь это утверждение.");
    }
}

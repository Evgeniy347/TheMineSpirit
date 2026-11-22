package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StageZoneTests {
    @Test
    void zone_SecondStage_ContainsFenceAndPlotsOfBothStages() {
        StageSite first = TownFixtures.flatSite();
        TownPlan plan = TownFixtures.flatPlan();
        StageSite second = StageSites.of(plan, 2, TownFixtures.LEVEL, TownRules.load());

        for (ColumnMask.Cell cell : first.enclosure().cells()) {
            assertTrue(
                    second.zone().contains(cell.x(), cell.z()),
                    "Зона второго этапа содержит огородку первого: граница закреплена и не переезжает"
                            + " Иначе тест zone_SecondStage_ContainsFenceAndPlotsOfBothStages перестанет ловить эту поломку. При смене zone_SecondStage_ContainsFenceAndPlotsOfBothStages проверь это утверждение.");
        }
        for (Placement place : plan.upToStage(2)) {
            for (ColumnMask.Cell cell : place.mask().cells()) {
                assertTrue(
                        second.zone().contains(cell.x(), cell.z()),
                        "Участок постройки '" + place.id() + "' вне зоны этапа " + second.stage()
                                + ": аудит и выравнивание прошли бы мимо постройки"
                                + " Иначе тест zone_SecondStage_ContainsFenceAndPlotsOfBothStages перестанет ловить эту поломку. При смене zone_SecondStage_ContainsFenceAndPlotsOfBothStages проверь это утверждение.");
            }
        }
        assertEquals(
                first.zone().count(),
                second.zone().count(),
                "Зона этапа — весь регион и не растёт: " + second.zone().count() + " против "
                        + first.zone().count()
                        + " Иначе тест zone_SecondStage_ContainsFenceAndPlotsOfBothStages перестанет ловить эту поломку. При смене zone_SecondStage_ContainsFenceAndPlotsOfBothStages проверь это утверждение.");
    }

    @Test
    void zone_CellOutsideFenceAndPlots_NotSettlementWork() {
        StageSite site = TownFixtures.flatSite();
        int far = site.zone().minX() - 500;

        assertFalse(
                site.zone().contains(far, site.zone().minZ()),
                "Клетка за оградой и участками — не зона поселения: яма там не работа ровности"
                        + " Иначе тест zone_CellOutsideFenceAndPlots_NotSettlementWork перестанет ловить эту поломку. При смене zone_CellOutsideFenceAndPlots_NotSettlementWork проверь это утверждение.");
    }
}

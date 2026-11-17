package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class PlotDemolishAccessTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void demolishersMayBreak_HouseStructureWithoutOwnBuilding_IsFalse() {
        assertFalse(
                PlotProtection.demolishersMayBreak(SITE, SITE.houseOrigin(), true),
                "Клетку дома без своей постройки снос не берёт: скан доступа не просит её ломать, иначе задача "
                        + "clear_access закрывается без изменения мира и плодит дубли");
    }

    @Test
    void demolishersMayBreak_OutsidePlan_IsTrue() {
        assertTrue(
                PlotProtection.demolishersMayBreak(
                        SITE,
                        new BlockCoord(
                                SITE.anchor().x() + 500,
                                SITE.anchor().y(),
                                SITE.anchor().z()),
                        false),
                "Природная клетка вне плана расчищается штатно: скан доступа обязан её видеть");
    }

    @Test
    void demolishersMayBreak_NaturalGroundInPlan_IsTrue() {
        assertTrue(
                PlotProtection.demolishersMayBreak(
                        SITE,
                        new BlockCoord(
                                SITE.houseOrigin().x(),
                                SITE.anchor().y(),
                                SITE.houseOrigin().z()),
                        false),
                "Природный грунт в клетке плана выравнивание снимает: скан доступа не должен его терять");
    }
}

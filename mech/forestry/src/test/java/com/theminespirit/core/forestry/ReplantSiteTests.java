package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplantSiteTests {

    private static ReplantSite.Plan town() {
        List<ReplantSite.Plot> plots =
                List.of(new ReplantSite.Plot("hut", 5, 5, 7, 9), new ReplantSite.Plot("orchard", 20, 20, 9, 9));
        return new ReplantSite.Plan(plots, (x, z) -> x >= 0 && x <= 40 && z == 0);
    }

    @Test
    void allowed_NoTown_Allows() {
        assertTrue(
                ReplantSite.allowed(null, new BlockCoord(6, 70, 6)),
                "Без плана поселения сажать можно везде: за участком лес восстанавливается сам");
    }

    @Test
    void allowed_OutsideRegion_Allows() {
        assertTrue(
                ReplantSite.allowed(town(), new BlockCoord(100, 70, 100)),
                "За контуром поселения сажать можно: дикий лес не участок построек");
    }

    @Test
    void allowed_OnBuildingPlot_Denies() {
        assertFalse(
                ReplantSite.allowed(town(), new BlockCoord(6, 70, 6)),
                "На участке постройки сажать нельзя: дерево вырастет в стенах хижины и сломает стройку");
    }

    @Test
    void allowed_InOrchard_Allows() {
        assertTrue(
                ReplantSite.allowed(town(), new BlockCoord(22, 70, 22)),
                "В саду плана сажать можно: сад — единственные деревья поселения");
    }

    @Test
    void allowed_OnStreet_Denies() {
        assertFalse(
                ReplantSite.allowed(town(), new BlockCoord(5, 70, 0)),
                "На улице сажать нельзя: саженец перегородит общий проход поселения");
    }
}

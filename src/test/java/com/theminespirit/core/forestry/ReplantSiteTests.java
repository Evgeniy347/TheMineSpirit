package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplantSiteTests {

    private static TownPlan town() {
        ColumnMask empty = new ColumnMask(0, 0, 41, 41, new BitSet());
        TownStreets streets = new TownStreets(ColumnMask.rect(0, 0, 40, 0), empty, empty);
        List<Placement> places = List.of(
                new Placement("hut", "Хижина", 1, 5, 5, 7, 9, Placement.Facing.NORTH),
                new Placement("grove", "Роща", 1, 20, 20, 9, 9, Placement.Facing.NORTH));
        return new TownPlan(
                new BlockCoord(20, 70, 20),
                ColumnMask.rect(0, 0, 40, 40),
                streets,
                places,
                new FenceLine(List.of(), List.of()),
                List.of());
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
    void allowed_InGrove_Allows() {
        assertTrue(
                ReplantSite.allowed(town(), new BlockCoord(22, 70, 22)),
                "В роще плана сажать можно: сад — единственные деревья поселения");
    }

    @Test
    void allowed_OnStreet_Denies() {
        assertFalse(
                ReplantSite.allowed(town(), new BlockCoord(5, 70, 0)),
                "На улице сажать нельзя: саженец перегородит общий проход поселения");
    }
}

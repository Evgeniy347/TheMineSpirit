package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlotTreeProtectionTests {

    @Test
    void treeObstacle_GrownTreeOnPlannedSaplingCell_IsObstacle() {
        assertTrue(
                PlotProtection.treeObstacle(true, false, "minecraft:jungle_sapling"),
                "Выросшее дерево на месте саженца — помеха плану: план ждёт саженец, а ствол срубают "
                        + "и сажают саженец заново");
    }

    @Test
    void treeObstacle_PlannedLogWall_IsNotObstacle() {
        assertFalse(
                PlotProtection.treeObstacle(true, false, "minecraft:jungle_log"),
                "Бревно, которое держит стена по плану, деревом-помехой не считается: иначе рубка снимает постройку");
    }

    @Test
    void treeObstacle_MarkedTrunk_IsNotObstacle() {
        assertFalse(
                PlotProtection.treeObstacle(true, true, null),
                "Ствол, поставленный игроком или духом, — постройка, а не природное дерево: защита его не отдаёт");
    }

    @Test
    void treeObstacle_NaturalTrunkOutsidePlan_IsObstacle() {
        assertTrue(
                PlotProtection.treeObstacle(true, false, null),
                "Природное дерево вне плана рубится всегда: план деревья не защищает");
    }
}

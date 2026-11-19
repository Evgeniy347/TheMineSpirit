package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlotTreeProtectionTests {

    @Test
    void treeObstacle_TrunkWithoutWoodenPlan_IsObstacle() {
        assertTrue(
                PlotProtection.treeObstacle(true, false),
                "Ствол вне плана дерева — природное дерево: план его игнорирует, дух рубит его в любом месте "
                        + "поселения и сажает саженец заново");
    }

    @Test
    void treeObstacle_TrunkOnWoodenPlanCell_IsNotObstacle() {
        assertFalse(
                PlotProtection.treeObstacle(true, true),
                "Бревно, которое держит стена по плану, — постройка, а не дерево: рубка не снимает дом");
    }

    @Test
    void treeObstacle_NotTreePart_IsNotObstacle() {
        assertFalse(
                PlotProtection.treeObstacle(false, false),
                "Листва и прочие блоки не дерево-помеха: правило деревьев их не касается");
    }
}

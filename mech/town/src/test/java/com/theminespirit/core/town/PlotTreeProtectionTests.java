package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlotTreeProtectionTests {

    @Test
    void treeObstacle_NaturalTrunk_IsObstacle() {
        assertTrue(
                PlotProtection.treeObstacle(true, false),
                "Ствол с кроной — природное дерево даже с отметкой духа: его рубят в любом месте поселения "
                        + "и сажают саженец заново");
    }

    @Test
    void treeObstacle_BuiltTrunk_IsNotObstacle() {
        assertFalse(
                PlotProtection.treeObstacle(true, true),
                "Бревно, поставленное стройкой в стену, — постройка, а не дерево: рубка не снимает дом");
    }

    @Test
    void treeObstacle_NotTreePart_IsNotObstacle() {
        assertFalse(
                PlotProtection.treeObstacle(false, false),
                "Листва и прочие блоки не дерево-помеха: правило деревьев их не касается");
    }
}

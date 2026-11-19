package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlotTreeProtectionTests {

    @Test
    void treeObstacle_UnmarkedTrunk_IsObstacle() {
        assertTrue(
                PlotProtection.treeObstacle(true, false),
                "Природный ствол — помеха плану, а не его блок: его рубят в любом месте поселения и "
                        + "сажают саженец заново");
    }

    @Test
    void treeObstacle_MarkedTrunk_IsNotObstacle() {
        assertFalse(
                PlotProtection.treeObstacle(true, true),
                "Ствол, поставленный игроком или духом, — постройка, а не природное дерево: защита его не отдаёт");
    }

    @Test
    void treeObstacle_NotTreePart_IsNotObstacle() {
        assertFalse(
                PlotProtection.treeObstacle(false, false),
                "Листва и прочие блоки не дерево-помеха: правило деревьев их не касается");
    }
}

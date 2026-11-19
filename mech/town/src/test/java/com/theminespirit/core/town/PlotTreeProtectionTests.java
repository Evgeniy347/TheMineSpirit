package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlotTreeProtectionTests {

    @Test
    void treeObstacle_UnmarkedTrunk_IsObstacle() {
        assertTrue(
                PlotProtection.treeObstacle(true, false),
                "Природный ствол без отметки духа — дерево-помеха: план его игнорирует, дух рубит его в любом "
                        + "месте поселения и сажает саженец заново");
    }

    @Test
    void treeObstacle_MarkedTrunk_IsNotObstacle() {
        assertFalse(
                PlotProtection.treeObstacle(true, true),
                "Бревно с отметкой поставлено стройкой: это стена или столб по плану, а не дерево, и защита его держит");
    }

    @Test
    void treeObstacle_NotTreePart_IsNotObstacle() {
        assertFalse(
                PlotProtection.treeObstacle(false, false),
                "Листва и прочие блоки не дерево-помеха: правило деревьев их не касается");
    }
}

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
                        + "месте поселения и сажает саженец заново"
                        + " Иначе тест treeObstacle_UnmarkedTrunk_IsObstacle перестанет ловить эту поломку. При смене treeObstacle_UnmarkedTrunk_IsObstacle проверь это утверждение.");
    }

    @Test
    void treeObstacle_MarkedTrunk_IsNotObstacle() {
        assertFalse(
                PlotProtection.treeObstacle(true, true),
                "Бревно с отметкой поставлено стройкой: это стена или столб по плану, а не дерево, и защита его держит"
                        + " Иначе тест treeObstacle_MarkedTrunk_IsNotObstacle перестанет ловить эту поломку. При смене treeObstacle_MarkedTrunk_IsNotObstacle проверь это утверждение.");
    }

    @Test
    void treeObstacle_NotTreePart_IsNotObstacle() {
        assertFalse(
                PlotProtection.treeObstacle(false, false),
                "Листва и прочие блоки не дерево-помеха: правило деревьев их не касается"
                        + " Иначе тест treeObstacle_NotTreePart_IsNotObstacle перестанет ловить эту поломку. При смене treeObstacle_NotTreePart_IsNotObstacle проверь это утверждение.");
    }
}

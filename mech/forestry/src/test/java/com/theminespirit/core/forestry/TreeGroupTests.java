package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class TreeGroupTests {
    @Test
    void leavesOnlyFloatingCloud_Of_IsPresentAndUngrounded() {
        TreeGrid grid = TreeGrid.ofLayers("..", "..", "..", "..", "..")
                .leaves(0, 5, 0)
                .leaves(1, 5, 0)
                .leaves(0, 5, 1);

        TreeGroup group = TreeGroup.of(new BlockCoord(0, 5, 0), grid).orElseThrow();

        assertTrue(
                group.logs().isEmpty(),
                "У облака листвы без брёвен нет ствола: это остаток кроны, иначе дух ищет ствол в облаке. При смене "
                        + "TreeGroup.of проверь строку размера ниже в тесте");
        assertEquals(
                3,
                group.leaves().size(),
                "Группа обязана собрать всю связную листву, иначе остаток кроны недобирается и висит. При смене "
                        + "связности проверь строку опоры ниже в тесте");
        assertFalse(
                group.grounded(),
                "Висящее облако листвы без опоры под нижним листом — не дерево на земле, иначе оно числится деревом. "
                        + "При смене grounded проверь строку на земле ниже в тесте");
    }

    @Test
    void leavesOnlyOnSoil_Of_IsGrounded() {
        TreeGrid grid = TreeGrid.ofLayers("..", "..", "..", "..", "..")
                .soil(0, 4, 0)
                .leaves(0, 5, 0)
                .leaves(1, 5, 0);

        TreeGroup group = TreeGroup.of(new BlockCoord(0, 5, 0), grid).orElseThrow();

        assertTrue(
                group.grounded(),
                "Листва на земле не висит: её не рубят как остаток кроны, иначе дерево на земле теряет листву. При "
                        + "смене grounded проверь строку выше в тесте");
    }
}

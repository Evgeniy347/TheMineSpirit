package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class TreeShapeStumpTests {
    @Test
    void detect_ShortStumpUnderNeighbourLeaves_ReturnsNotATrunk() {
        TreeGrid grid = new TreeGrid()
                .log(0, 1, 0)
                .log(0, 2, 0)
                .soil(0, 0, 0)
                .leaves(1, 2, 0)
                .leaves(1, 3, 0)
                .leaves(0, 3, 0);

        NotATree rejection = assertInstanceOf(
                NotATree.class,
                TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Пень из двух брёвен под чужой кроной обязан быть отвергнут: листья соседа делают его похожим "
                        + "на ствол, и без проверки высоты дух снова выберет недоступный остаток (observe14 A4)");
        assertEquals(
                NotATree.Reason.NOT_A_TRUNK,
                rejection.reason(),
                "Короткий остаток — не ствол: настоящее дерево выше, и именно высота отличает пень от дерева");
    }
}

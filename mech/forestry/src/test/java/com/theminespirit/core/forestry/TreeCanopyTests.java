package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeCanopyTests {
    private static Tree trunk() {
        return new Tree(
                new BlockCoord(0, 64, 0),
                List.of(new BlockCoord(0, 64, 0), new BlockCoord(0, 65, 0), new BlockCoord(0, 66, 0)));
    }

    @Test
    void plan_OwnLeaves_TopDown() {
        TreeGrid grid = new TreeGrid()
                .log(0, 64, 0)
                .log(0, 65, 0)
                .log(0, 66, 0)
                .leaves(0, 67, 0)
                .leaves(1, 66, 0)
                .leaves(-1, 66, 0)
                .leaves(0, 65, 1);

        List<BlockCoord> plan = TreeCanopy.plan(trunk(), grid);

        assertEquals(
                List.of(
                        new BlockCoord(0, 67, 0),
                        new BlockCoord(-1, 66, 0),
                        new BlockCoord(1, 66, 0),
                        new BlockCoord(0, 65, 1)),
                plan,
                "Крона снимается сверху вниз: нижняя листва не держит верхнюю, и дух не рубит дважды одну и ту же "
                        + "ветку. Иначе листва над головой останется висеть. При смене TreeCanopy.plan проверь "
                        + "строку чужого дерева ниже в тесте");
    }

    @Test
    void plan_ForeignTreeItself_NotTaken() {
        TreeGrid grid = new TreeGrid()
                .log(0, 64, 0)
                .log(0, 65, 0)
                .log(0, 66, 0)
                .leaves(0, 67, 0)
                .log(5, 64, 0)
                .leaves(5, 67, 0)
                .leaves(4, 67, 0);

        List<BlockCoord> plan = TreeCanopy.plan(trunk(), grid);

        assertEquals(
                List.of(new BlockCoord(0, 67, 0)),
                plan,
                "Листва соседнего дерева не принадлежит этому: рубить её значит обездолить соседа, иначе крона "
                        + "соседа тает. При смене связности проверь строку ели ниже в тесте");
    }

    @Test
    void plan_SparseSpruceWideLayer_TakenWithinMargin() {
        TreeGrid grid = new TreeGrid().log(0, 64, 0).log(0, 65, 0).log(0, 66, 0);
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                grid.leaves(dx, 64, dz);
            }
        }

        List<BlockCoord> plan = TreeCanopy.plan(trunk(), grid);

        assertTrue(
                plan.contains(new BlockCoord(5, 64, 0)),
                "Широкий нижний слой ели целиком в кроне: план, обрезанный в 4 блока от ствола, оставлял висеть "
                        + "крайние клетки слоя, иначе дух заканчивает дерево с листвой. При смене предела кроны "
                        + "проверь строку за пределом ниже в тесте");
        assertFalse(
                plan.contains(new BlockCoord(7, 64, 0)),
                "Крона не расползается за предел 6 блоков от стволов, иначе рубка тянет листву соседнего леса. При "
                        + "смене предела 6 проверь строку ели выше в тесте");
    }

    @Test
    void plan_Planks_NotTouched() {
        TreeGrid grid = new TreeGrid()
                .log(0, 64, 0)
                .log(0, 65, 0)
                .log(0, 66, 0)
                .leaves(0, 67, 0)
                .planks(1, 67, 0);

        assertTrue(
                TreeCanopy.plan(trunk(), grid).stream().noneMatch(at -> at.equals(new BlockCoord(1, 67, 0))),
                "Руками ломается только листва: доски игрока в кроне не трогаются, иначе рубка сносит стройку. При "
                        + "смене проверки искусственных блоков проверь строку выше в тесте");
    }

    @Test
    void plan_OrphanLeavesAboveStump_InPlan() {
        Tree stump = new Tree(new BlockCoord(0, 64, 0), List.of(new BlockCoord(0, 64, 0), new BlockCoord(0, 68, 0)));
        TreeGrid grid = new TreeGrid().log(0, 64, 0).log(0, 68, 0).leaves(2, 68, 0);

        assertTrue(
                TreeCanopy.plan(stump, grid).contains(new BlockCoord(2, 68, 0)),
                "Висящая листва над пнем снимается вместе с остатком ствола: иначе кусок старой кроны висит над "
                        + "новой посадкой и листва падает духу на голову. При смене добора сиротской листвы "
                        + "проверь строку листвы у чужого дерева ниже в тесте");
    }

    @Test
    void plan_LeavesNearForeignTree_Kept() {
        Tree stump = new Tree(new BlockCoord(0, 64, 0), List.of(new BlockCoord(0, 64, 0), new BlockCoord(0, 68, 0)));
        TreeGrid grid = new TreeGrid()
                .log(0, 64, 0)
                .log(0, 68, 0)
                .log(6, 68, 0)
                .leaves(5, 68, 0)
                .leaves(6, 69, 0);

        assertFalse(
                TreeCanopy.plan(stump, grid).contains(new BlockCoord(5, 68, 0)),
                "Листва, ближняя к чужому стволу, не трогается: иначе добор сирот обездоливает соседнее дерево и "
                        + "оголяет сад. При смене правила ownedBy проверь строку без чужого ствола выше в тесте");
    }
}

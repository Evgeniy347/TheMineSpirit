package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChopPlanRestTests {
    private static final BlockCoord STAND = new BlockCoord(3, 1, 1);

    private static TreeGrid thickOak() {
        return TreeGrid.ofLayers(
                "####\n####\n####\n####",
                ".LL.\n.LL.\n....\n....",
                ".LL.\n.LL.\n....\n....",
                "L.L.\n.L..\n...L\n....",
                "@@@@\n@L@@\n@@@@\n....");
    }

    @Test
    void of_ThickOakWithBranches_PlansWholeTrunkDownToSoil() {
        TreeGrid grid = thickOak();
        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(new BlockCoord(1, 1, 1), grid),
                "Дуб с толстым основанием и ветками — дерево: иначе его не рубят вовсе");
        ChopPlan plan = ChopPlan.of(tree, STAND);
        assertEquals(
                grid.logs(),
                Set.copyOf(plan.order()),
                "План рубки дуба с толстым основанием берёт все брёвна — и ветки, и четыре нижних над землёй: "
                        + "иначе остаётся пенёк (INC-90)");
        List<BlockCoord> bottom =
                plan.order().subList(plan.order().size() - 4, plan.order().size());
        assertTrue(
                bottom.stream().allMatch(log -> log.y() == 1),
                "Нижний слой толстого ствола рубится последним, сверху вниз: " + bottom);
    }

    @Test
    void rest_FirstPassLeftBaseAndBranch_SecondPassTakesThemAll() {
        TreeGrid grid = thickOak();
        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(new BlockCoord(1, 1, 1), grid),
                "Дуб с толстым основанием и ветками — дерево: иначе его не рубят вовсе");
        ChopPlan plan = ChopPlan.of(tree, STAND);
        Set<BlockCoord> left = Set.of(new BlockCoord(1, 1, 1), new BlockCoord(2, 1, 0), new BlockCoord(3, 3, 2));
        plan.order().stream().filter(log -> !left.contains(log)).forEach(log -> grid.air(log.x(), log.y(), log.z()));
        ChopPlan rest = plan.rest(grid, new BlockCoord(0, 1, 2));
        assertEquals(
                List.of(new BlockCoord(3, 3, 2), new BlockCoord(2, 1, 0), new BlockCoord(1, 1, 1)),
                rest.order(),
                "Со второй стоянки добиваются все оставшиеся брёвна — ветка и нижние у земли, сверху вниз: "
                        + "оставшееся после первого прохода не может молча стать пеньком (INC-90)");
        left.forEach(log -> grid.air(log.x(), log.y(), log.z()));
        assertEquals(
                List.of(),
                plan.rest(grid, STAND).order(),
                "Срубленное дерево не даёт остатка: вторая стоянка нужна, только если что-то осталось");
    }
}

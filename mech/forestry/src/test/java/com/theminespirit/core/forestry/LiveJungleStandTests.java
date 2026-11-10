package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveJungleStandTests {
    private static final Path WORLD = Path.of("src/test/resources/live/chopxz-jungle-2x2.json");
    private static final BlockCoord BASE = new BlockCoord(-542, 63, -577);

    @Test
    void liveJungleTwoByTwo_StandAboveGroundWindow_ChopsEveryLog() throws IOException {
        Map<BlockCoord, String> blocks = ForestryScenarioTests.blocks(WORLD);
        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(BASE, new ForestryScenarioTests.SnapshotView(blocks, "jungle")),
                "Живой джунглевый ствол обязан разобраться как дерево");
        long baseLogs = tree.logs().stream().filter(log -> log.y() == BASE.y()).count();
        assertEquals(4, baseLogs, "Живой джунглевый ствол обязан быть 2×2: " + tree.logs());

        ChopStands.View view = new ChopStands.View() {
            @Override
            public boolean standable(BlockCoord feet) {
                return LiveJungleStandTests.free(blocks, feet)
                        && LiveJungleStandTests.free(blocks, above(feet))
                        && LiveJungleStandTests.solid(blocks, below(feet));
            }

            @Override
            public boolean free(BlockCoord cell) {
                return LiveJungleStandTests.free(blocks, cell);
            }
        };
        List<BlockCoord> stands = ChopStands.stands(tree, view);

        assertFalse(stands.isEmpty(), "У живого 2×2 дерева обязана найтись стоянка вплотную к стволу");
        assertTrue(
                stands.stream().allMatch(feet -> adjacent(feet, tree.logs())),
                "Стоянка обязана быть вплотную к стволу по x и z, а не в стороне: " + stands);
        List<BlockCoord> aboveWindow =
                stands.stream().filter(feet -> feet.y() > BASE.y() + 1).toList();
        assertTrue(
                !aboveWindow.isEmpty(),
                "Высота стоянки не ограничена основанием дерева: у живого 2×2 есть клетки вплотную к стволу "
                        + "выше старого окна основания, иначе среди бугров, лиан и листвы стоянки нет: " + stands);

        ChopPlan plan = ChopPlan.of(tree, aboveWindow.get(0));
        assertEquals(
                tree.logs().size(),
                plan.order().size(),
                "С найденной стоянки рубится всё дерево, а не часть ствола: " + plan.order());
        assertEquals(
                tree.logs().stream().mapToInt(BlockCoord::y).max().orElseThrow(),
                plan.order().get(0).y(),
                "Рубка начинается с верхнего бревна: иначе ствол падает на духа");
    }

    private static boolean free(Map<BlockCoord, String> blocks, BlockCoord cell) {
        String block = blocks.get(cell);
        return block == null
                || "minecraft:air".equals(block)
                || "minecraft:short_grass".equals(block)
                || "minecraft:fern".equals(block);
    }

    private static boolean solid(Map<BlockCoord, String> blocks, BlockCoord cell) {
        String block = blocks.get(cell);
        return block != null
                && !"minecraft:air".equals(block)
                && !block.endsWith("_leaves")
                && !block.endsWith(":vine")
                && !block.endsWith("_vines")
                && !"minecraft:short_grass".equals(block)
                && !"minecraft:fern".equals(block);
    }

    private static BlockCoord above(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() + 1, cell.z());
    }

    private static BlockCoord below(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() - 1, cell.z());
    }

    private static boolean adjacent(BlockCoord feet, List<BlockCoord> logs) {
        return logs.stream()
                .anyMatch(log -> Math.abs(log.x() - feet.x()) <= 1
                        && Math.abs(log.z() - feet.z()) <= 1
                        && !(log.x() == feet.x() && log.z() == feet.z()));
    }
}

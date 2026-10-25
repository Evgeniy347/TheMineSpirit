package com.theminespirit.core.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.SettlementLayout;
import java.util.List;
import java.util.stream.LongStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

class SceneGeneratorTests {
    private static final int PLOT = SettlementLayout.HALF;

    @Property
    void generate_SameSeed_SameScene(@ForAll long seed) {
        ScenePlan first = SceneGenerator.generate(seed);
        ScenePlan second = SceneGenerator.generate(seed);
        assertEquals(
                first.heights(),
                second.heights(),
                "Сид " + seed + " дал два разных рельефа: --seed N не повторит упавшую сцену");
        assertEquals(
                first.blocks(),
                second.blocks(),
                "Сид " + seed + " дал разные объекты сцены: повтор упавшего теста шёл бы на другом мире");
        assertEquals(first.wood(), second.wood(), "Сид " + seed + " дал разную породу: повтор не тот же мир");
    }

    @Test
    void generate_DifferentSeeds_DifferentRelief() {
        long distinct = LongStream.range(0, 8)
                .mapToObj(seed -> SceneGenerator.generate(seed).heights())
                .distinct()
                .count();
        assertEquals(8, distinct, "8 сидов дали повторяющийся рельеф: новый сид должен давать новый мир");
    }

    @Property
    void generate_AnySeed_ReliefWithinFourBlocksAndNotFlat(@ForAll long seed) {
        ScenePlan plan = SceneGenerator.generate(seed);
        int plotMax = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        for (int x = -SceneGenerator.RADIUS; x <= SceneGenerator.RADIUS; x++) {
            for (int z = -SceneGenerator.RADIUS; z <= SceneGenerator.RADIUS; z++) {
                int height = plan.height(x, z);
                assertTrue(
                        Math.abs(height) <= SceneGenerator.RELIEF,
                        "Сид " + seed + ": высота " + height + " в (" + x + ", " + z
                                + ") за пределом ±4 — рельеф сцены выходит из решения владельца");
                min = Math.min(min, height);
                if (Math.abs(x) <= PLOT && Math.abs(z) <= PLOT) {
                    plotMax = Math.max(plotMax, height);
                }
            }
        }
        assertTrue(plotMax >= 3, "Сид " + seed + ": холм на участке " + plotMax + " < 3 — участок почти плоский");
        assertEquals(
                -SceneGenerator.RELIEF,
                min,
                "Сид " + seed + ": низины глубиной 4 нет — подсыпка у края участка не проверяется");
    }

    @Property
    void generate_AnySeed_CaveUnderPlotBelowSurface(@ForAll long seed) {
        ScenePlan plan = SceneGenerator.generate(seed);
        List<SceneBlock> cave = blocksOf(plan, "air");
        assertFalse(cave.isEmpty(), "Сид " + seed + ": пещеры под участком нет — карман под ящиком не проверяется");
        for (SceneBlock cell : cave) {
            assertTrue(
                    Math.abs(cell.x()) <= PLOT && Math.abs(cell.z()) <= PLOT,
                    "Сид " + seed + ": пещера в " + cell + " вне участка — она должна быть под ним");
            assertTrue(
                    cell.y() <= plan.height(cell.x(), cell.z()) - 2 && cell.y() > plan.bottom(),
                    "Сид " + seed + ": клетка пещеры " + cell + " не под землёй — это яма, а не пещера");
        }
    }

    @Property
    void generate_AnySeed_TreesWaterAndRuins(@ForAll long seed) {
        ScenePlan plan = SceneGenerator.generate(seed);
        String log = plan.wood() + "_log";
        assertTrue(
                SceneGenerator.WOODS.contains(plan.wood()),
                "Сид " + seed + ": порода " + plan.wood() + " — не джунгли и не дуб");
        long trunksOnPlot = blocksOf(plan, log).stream()
                .filter(b -> b.y() == plan.height(b.x(), b.z()) + 1)
                .filter(b -> Math.abs(b.x()) <= PLOT && Math.abs(b.z()) <= PLOT)
                .count();
        long trunks = blocksOf(plan, log).stream()
                .filter(b -> b.y() == plan.height(b.x(), b.z()) + 1)
                .count();
        assertTrue(trunksOnPlot >= 4, "Сид " + seed + ": деревьев на участке " + trunksOnPlot + " < 4");
        assertTrue(trunks - trunksOnPlot >= 8, "Сид " + seed + ": деревьев за участком мало — не из чего строить");
        assertFalse(blocksOf(plan, "water").isEmpty(), "Сид " + seed + ": воды в низине нет");
        List<SceneBlock> ruins =
                plan.blocks().stream().filter(SceneBlock::settlement).toList();
        assertTrue(
                ruins.stream().anyMatch(b -> b.block().endsWith("_fence")) && ruins.size() >= 9,
                "Сид " + seed + ": остатков старых построек " + ruins.size() + " — без старой ограды INC-70 не виден");
        for (SceneBlock ruin : ruins) {
            assertTrue(
                    Math.abs(ruin.x()) <= PLOT && Math.abs(ruin.z()) <= PLOT,
                    "Сид " + seed + ": старая постройка " + ruin + " вне участка — она должна мешать выравниванию");
        }
    }

    @Property
    void generate_AnySeed_SpawnCenterFree(@ForAll long seed) {
        ScenePlan plan = SceneGenerator.generate(seed);
        for (SceneBlock block : plan.blocks()) {
            boolean center = Math.abs(block.x()) <= 1 && Math.abs(block.z()) <= 1;
            assertFalse(
                    center && block.y() > plan.height(block.x(), block.z()),
                    "Сид " + seed + ": " + block + " над центром — духи появятся в дереве или постройке");
        }
    }

    private static List<SceneBlock> blocksOf(ScenePlan plan, String block) {
        return plan.blocks().stream()
                .filter(entry -> entry.block().equals(block))
                .toList();
    }
}

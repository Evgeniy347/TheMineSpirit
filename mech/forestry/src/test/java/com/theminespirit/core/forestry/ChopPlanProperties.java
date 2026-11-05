package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

class ChopPlanProperties {
    @Property(tries = 200)
    void of_AnyTreeAndStand_OrdersAllLogsTopDown(@ForAll long seed) {
        Tree tree = randomTree(seed);
        BlockCoord from = new BlockCoord((int) (seed % 5) - 2, 63, (int) (seed % 7) - 3);

        ChopPlan plan = ChopPlan.of(tree, from);

        assertEquals(
                tree.logs().size(),
                plan.order().size(),
                "План обязан нести каждое бревно дерева ровно один раз: потерянное бревно остаётся висеть "
                        + "в воздухе, а дубликат — рубится дважды");
        assertEquals(
                Set.copyOf(tree.logs()),
                Set.copyOf(plan.order()),
                "Множество целей плана обязано совпасть с брёвнами дерева: план не добавляет и не теряет клеток");
        for (int i = 1; i < plan.order().size(); i++) {
            BlockCoord previous = plan.order().get(i - 1);
            BlockCoord current = plan.order().get(i);
            assertTrue(
                    previous.y() > current.y()
                            || (previous.y() == current.y()
                                    && distanceSquared(previous, from) >= distanceSquared(current, from)),
                    "Порядок — сверху вниз, на одной высоте дальнее первым (INC-49): бревно " + current
                            + " нарушает порядок после " + previous + " при стоянке " + from);
        }
    }

    private static long distanceSquared(BlockCoord first, BlockCoord second) {
        long dx = (long) first.x() - second.x();
        long dy = (long) first.y() - second.y();
        long dz = (long) first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static Tree randomTree(long seed) {
        Random random = new Random(seed);
        BlockCoord base = new BlockCoord(0, 64, 0);
        List<BlockCoord> logs = new ArrayList<>();
        logs.add(base);
        int extra = random.nextInt(20);
        for (int i = 0; i < extra; i++) {
            BlockCoord coord = new BlockCoord(random.nextInt(5) - 2, 62 + random.nextInt(12), random.nextInt(5) - 2);
            if (!logs.contains(coord)) {
                logs.add(coord);
            }
        }
        return new Tree(base, logs);
    }
}

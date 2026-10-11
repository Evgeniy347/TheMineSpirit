package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class ChopPlanProperties {
    @Property(tries = 200)
    void of_AnyTreeAndReach_ReachablePartitionConsistent(
            @ForAll @IntRange(min = 0, max = 5) int reach, @ForAll long seed) {
        Tree tree = randomTree(seed);

        ChopPlan plan = ChopPlan.of(tree, reach);

        assertEquals(tree.logs().size(), plan.reachable().size() + plan.leftStanding(),
                "Достижимые и оставленные брёвна обязаны в сумме дать все брёвна дерева: потерянное бревно "
                        + "остаётся висеть в воздухе и не подбирается");
        int limit = tree.base().y() + reach;
        assertEquals(tree.logs().stream().filter(coord -> coord.y() <= limit).toList(), plan.reachable(),
                "Достижимы ровно те брёвна, чья высота не выше base.y + reach: граница делит ствол "
                        + "на рубку и остаток, и сдвиг границы обязан двигать разрез без дыр");
        assertEquals(tree.logs().size() - plan.reachable().size(), plan.leftStanding(),
                "Число оставленных брёвен обязано равняться разности всех и достижимых: расхождение "
                        + "обманывает игрока в итоговом сообщении");
    }

    private static Tree randomTree(long seed) {
        Random random = new Random(seed);
        BlockCoord base = new BlockCoord(0, 64, 0);
        List<BlockCoord> logs = new ArrayList<>();
        logs.add(base);
        int extra = random.nextInt(20);
        for (int i = 0; i < extra; i++) {
            BlockCoord coord = new BlockCoord(random.nextInt(5) - 2, 62 + random.nextInt(12),
                    random.nextInt(5) - 2);
            if (!logs.contains(coord)) {
                logs.add(coord);
            }
        }
        return new Tree(base, logs);
    }
}
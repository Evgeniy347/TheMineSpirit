package com.theminespirit.core.board.audit;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask.Cell;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.TownPlan;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class FenceEnvelopes {
    private static final String SOURCE = "loop";

    private FenceEnvelopes() {}

    static void collect(TownPlan plan, int levelY, List<Expectation> out) {
        Objects.requireNonNull(plan, "План не может быть null: ограду брать негде");
        FenceLine fence = plan.fence();
        Set<Integer> gates = Set.copyOf(fence.gates());
        for (int index = 0; index < fence.posts().size(); index++) {
            Cell post = fence.posts().get(index);
            String block = gates.contains(index) ? "minecraft:oak_fence_gate" : "minecraft:oak_fence";
            out.add(new Expectation(
                    new BlockCoord(post.x(), levelY - 1, post.z()),
                    Wanted.ground(levelY),
                    SourceRef.wall(SOURCE),
                    Access.SPIRIT));
            out.add(new Expectation(
                    new BlockCoord(post.x(), levelY, post.z()),
                    Wanted.block(block),
                    SourceRef.wall(SOURCE),
                    Access.SPIRIT));
        }
    }
}

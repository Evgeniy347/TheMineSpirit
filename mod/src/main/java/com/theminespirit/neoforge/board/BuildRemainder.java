package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.plan.TaskNode;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

final class BuildRemainder {
    private static final Set<BoardCase> WOODEN = EnumSet.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE);

    private BuildRemainder() {}

    static Map<String, Integer> left(ServerLevel level, BoardCase build, TaskNode plan, Map<String, Integer> need) {
        SettlementPlotStore store = SettlementPlotStore.get(level);
        Optional<String> wood = store.plot()
                .map(plot -> SettlementPlotStore.liveSite(level)
                        .map(site -> BuildingWoodChoice.stored(level, build, site, plot.wood()))
                        .orElse(plot.wood()));
        if (!WOODEN.contains(build) || wood.isEmpty()) {
            return need;
        }
        Map<String, Integer> left = new HashMap<>();
        need.forEach((item, count) -> left.merge(WoodSpecies.swap(item, wood.orElseThrow()), count, Integer::sum));
        FirstStepsRunner.minusPlaced(level, plan, wood.orElseThrow(), left);
        return left;
    }
}

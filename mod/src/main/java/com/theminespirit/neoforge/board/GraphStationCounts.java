package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardConfig;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

final class GraphStationCounts {
    private static final int STATION_BELOW = 8;
    private static final int STATION_ABOVE = 8;

    private GraphStationCounts() {}

    static Map<String, Integer> count(
            ServerLevel level, FirstStepsRunner.StageScan scan, SupplyGraph graph, Map<String, Built> buildings) {
        Set<String> stations = graph.costs().stationCapacity().keySet();
        Map<String, Integer> found = new LinkedHashMap<>();
        BlockCoord yard = scan.stage().workYard();
        int radius = BoardConfig.current().facts().stationSearchRadius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -STATION_BELOW; dy <= STATION_ABOVE; dy++) {
                    BlockPos at = new BlockPos(yard.x() + dx, yard.y() + dy, yard.z() + dz);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    String id = id(level.getBlockState(at));
                    if (stations.contains(id)) {
                        found.merge(id, 1, Integer::sum);
                    }
                }
            }
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        stations.stream()
                .sorted()
                .forEach(id -> counts.put(id, buildings.get(id) == Built.BUILT ? found.getOrDefault(id, 0) : 0));
        return counts;
    }

    static String id(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }
}

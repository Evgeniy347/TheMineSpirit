package com.theminespirit.neoforge.board;

import com.theminespirit.core.base.NamespacedId;
import com.theminespirit.core.board.BoardConfig;
import com.theminespirit.core.forestry.api.TreeSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.OrchardPlot;
import com.theminespirit.core.town.StageSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

final class TreeCenterLogs {
    private static final int BELOW = 4;
    private static final int ABOVE = 16;

    private TreeCenterLogs() {}

    static List<BlockCoord> centers(StageSite site) {
        List<BlockCoord> centers = new ArrayList<>(site.orchardSaplings());
        for (OrchardPlot plot : site.extraOrchards()) {
            centers.addAll(plot.saplings());
        }
        return List.copyOf(centers);
    }

    static void collect(ServerLevel level, BlockCoord center, Map<String, List<BlockCoord>> logs) {
        for (int dx = -BoardConfig.current().facts().treeCenterRadius();
                dx <= BoardConfig.current().facts().treeCenterRadius();
                dx++) {
            for (int dz = -BoardConfig.current().facts().treeCenterRadius();
                    dz <= BoardConfig.current().facts().treeCenterRadius();
                    dz++) {
                for (int dy = -BELOW; dy <= ABOVE; dy++) {
                    BlockPos pos = new BlockPos(center.x() + dx, center.y() + dy, center.z() + dz);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    String id = BuiltInRegistries.BLOCK
                            .getKey(level.getBlockState(pos).getBlock())
                            .getPath();
                    Optional<TreeSpecies> species = TreeSpecies.ofLog(NamespacedId.MINECRAFT + id);
                    if (species.isEmpty()) {
                        continue;
                    }
                    List<BlockCoord> cells =
                            logs.computeIfAbsent(species.orElseThrow().id(), key -> new ArrayList<>());
                    BlockCoord at = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
                    if (!cells.contains(at)) {
                        cells.add(at);
                    }
                }
            }
        }
    }
}

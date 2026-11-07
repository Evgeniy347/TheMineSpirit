package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphSiteFacts;
import com.theminespirit.core.board.graph.HighStrays;
import com.theminespirit.core.building.ScaffoldPlan;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SettlementScan;
import com.theminespirit.core.town.StrayGroups;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

final class HighStrayInputs {
    record Input(List<StrayGroups.Cell> low, List<GraphSiteFacts.HighStray> high) {
        Input {
            low = List.copyOf(low);
            high = List.copyOf(high);
        }
    }

    private HighStrayInputs() {}

    static Input of(ServerLevel level, SettlementScan.Strays found, int levelY) {
        Set<BlockCoord> protectedCells = ScaffoldStore.get(level).protectedCells(level.getGameTime());
        HighStrays.Split split = HighStrays.split(
                found.high().stream()
                        .filter(cell -> !protectedCells.contains(cell.at()))
                        .toList(),
                SpiritReach.current(),
                (x, z) -> levelY);
        List<StrayGroups.Cell> low = new ArrayList<>();
        found.strays().stream()
                .filter(cell -> !protectedCells.contains(cell.at()))
                .forEach(low::add);
        low.addAll(split.reachable());
        return new Input(low, highStrays(level, split.high()));
    }

    private static List<GraphSiteFacts.HighStray> highStrays(ServerLevel level, List<HighStrays.Stalk> stalks) {
        SpiritReach reach = SpiritReach.current();
        List<ScaffoldPlan.Stalk> input = stalks.stream()
                .map(stalk -> new ScaffoldPlan.Stalk(stalk.id(), stalk.cells()))
                .toList();
        ScaffoldPlan.Plan plan = ScaffoldPlan.plan(
                input,
                reach,
                (x, z) -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
                anchor -> freeAnchor(level, anchor));
        List<GraphSiteFacts.HighStray> facts = new ArrayList<>();
        for (ScaffoldPlan.Scaffold scaffold : plan.scaffolds()) {
            List<GraphFacts.Position> cells = scaffold.targetsTopDown().stream()
                    .map(cell -> new GraphFacts.Position(cell.x(), cell.y(), cell.z()))
                    .toList();
            facts.add(new GraphSiteFacts.HighStray(
                    scaffold.id(),
                    new GraphFacts.Position(
                            scaffold.anchor().x(),
                            scaffold.anchor().y(),
                            scaffold.anchor().z()),
                    scaffold.height(),
                    scaffold.stalks(),
                    cells,
                    blockCounts(level, scaffold.targetsTopDown())));
        }
        return List.copyOf(facts);
    }

    private static boolean freeAnchor(ServerLevel level, BlockCoord anchor) {
        BlockPos feet = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        if (!level.isLoaded(feet)) {
            return false;
        }
        return level.getBlockState(feet).canBeReplaced()
                && level.getBlockState(feet.above()).canBeReplaced()
                && !level.getBlockState(feet.below()).isAir();
    }

    private static Map<String, Integer> blockCounts(ServerLevel level, List<BlockCoord> cells) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BlockCoord at : cells) {
            String block = BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(new BlockPos(at.x(), at.y(), at.z()))
                            .getBlock())
                    .toString();
            counts.merge(block, 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }
}

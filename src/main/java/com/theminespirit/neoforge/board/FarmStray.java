package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphSiteFacts;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

final class FarmStray {
    private static final int RADIUS = 16;
    private static final int BELOW = 2;
    private static final int ABOVE = 2;
    private static final String BUILDER = "farm";

    private FarmStray() {}

    static List<BlockCoord> cells(ServerLevel level, StageSite site, TownPlan plan) {
        BlockCoord anchor = site.anchor();
        PlacedBlocksData placed = PlacedBlocksData.get(level);
        Map<String, BlockCoord> found = new LinkedHashMap<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int x = anchor.x() + dx;
                int z = anchor.z() + dz;
                if (inside(plan.placements(), x, z)) {
                    continue;
                }
                for (int dy = -BELOW; dy <= ABOVE; dy++) {
                    BlockPos pos = new BlockPos(x, anchor.y() + dy, z);
                    if (!level.isLoaded(pos) || placed.isMarked(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof FarmBlock || state.getBlock() instanceof CropBlock) {
                        found.putIfAbsent(
                                pos.getX() + "," + pos.getY() + "," + pos.getZ(),
                                new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
                    }
                }
            }
        }
        return List.copyOf(found.values());
    }

    static PlanDemolition.Collected withFarm(
            PlanDemolition.Collected collected,
            ServerLevel level,
            StageSite site,
            TownPlan town,
            PlotPlanBlocks.Plan plan) {
        List<BlockCoord> remove = new ArrayList<>(collected.remove());
        List<String> notes = new ArrayList<>(collected.notes());
        collect(level, site, town, plan, remove, notes);
        return new PlanDemolition.Collected(
                remove,
                List.copyOf(notes),
                collected.excluded(),
                collected.unreachable(),
                collected.stand(),
                collected.top(),
                collected.lowest(),
                collected.treeCells());
    }

    static void collect(
            ServerLevel level,
            StageSite site,
            TownPlan plan,
            PlotPlanBlocks.Plan scheme,
            List<BlockCoord> remove,
            List<String> notes) {
        for (BlockCoord cell : cells(level, site, plan)) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            String id = BlockStates.idOf(level.getBlockState(pos));
            if (DemolitionGuard.protectedForDemolish(level, cell, id, scheme)) {
                continue;
            }
            remove.add(cell);
            if (notes.size() < 4) {
                notes.add(cell + " — старая грядка вне участка плана: разбираем");
            }
        }
    }

    static List<GraphSiteFacts.Stray> facts(ServerLevel level, StageSite site, TownPlan plan) {
        List<GraphSiteFacts.Stray> strays = new ArrayList<>();
        for (BlockCoord cell : cells(level, site, plan)) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            String type = BlockStates.idOf(level.getBlockState(pos));
            strays.add(new GraphSiteFacts.Stray(
                    "stray:" + type + "@" + cell.x() + "," + cell.y() + "," + cell.z(),
                    type,
                    new GraphFacts.Position(cell.x(), cell.y(), cell.z()),
                    BUILDER,
                    Map.of(type, 1)));
        }
        return List.copyOf(strays);
    }

    private static boolean inside(List<Placement> placements, int x, int z) {
        for (Placement place : placements) {
            if (place.covers(x, z)) {
                return true;
            }
        }
        return false;
    }
}

package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.ProtectionConfig;
import com.theminespirit.core.policy.SettlementBreak;
import com.theminespirit.core.town.BuildingSeeds;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class DemolitionGuard {
    private static final BlockProtection PROTECTION = ProtectionConfig.load();
    private static final String AIR = "minecraft:air";

    private DemolitionGuard() {}

    static boolean protectedForDemolish(ServerLevel level, BlockCoord at, String blockId, PlotPlanBlocks.Plan plan) {
        return protectedForDemolish(level, at, blockId, plan, false);
    }

    static boolean protectedForDemolish(
            ServerLevel level, BlockCoord at, String blockId, PlotPlanBlocks.Plan plan, boolean ignorePlayerMark) {
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        PlacedBlocksData placed = PlacedBlocksData.get(level);
        if (placed.isSpiritMarked(pos)) {
            return false;
        }
        List<String> ids = neighbours(level, pos);
        SettlementBreak.Origin origin = placed.isMarked(pos) && !ignorePlayerMark
                ? SettlementBreak.Origin.PLAYER
                : SettlementBreak.Origin.UNKNOWN;
        SettlementBreak.Facts facts = new SettlementBreak.Facts(origin, true, plan.planned(at), blockId, false, false);
        BlockProtection.Decision decision = SettlementBreak.decide(PROTECTION, facts, () -> ids);
        return decision instanceof BlockProtection.Refused || BuildingSeeds.adjacentToStructure(ids);
    }

    static void claimForCleanup(ServerLevel level, PlotPlanBlocks.Plan plan, FloatingBlocks.Finding finding) {
        PlacedBlocksData placed = PlacedBlocksData.get(level);
        for (BlockCoord cell : finding.cells()) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            String id = BlockStates.idOf(level.getBlockState(pos));
            if (protectedForDemolish(level, cell, id, plan, true)) {
                continue;
            }
            placed.markSpirit(pos);
        }
    }

    private static List<String> neighbours(ServerLevel level, BlockPos pos) {
        List<String> ids = new ArrayList<>(BlockProtection.NEIGHBOUR_COUNT);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos near = pos.offset(dx, dy, dz);
                    ids.add(level.isLoaded(near) ? BlockStates.idOf(level.getBlockState(near)) : AIR);
                }
            }
        }
        return ids;
    }
}

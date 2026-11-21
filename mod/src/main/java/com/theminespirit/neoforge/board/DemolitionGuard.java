package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.SettlementBreak;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.BuildingSeeds;
import com.theminespirit.neoforge.BlockStates;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class DemolitionGuard {
    private static final BlockProtection PROTECTION =
            DataTables.INSTANCE.protection().current();
    private static final String AIR = "minecraft:air";

    private DemolitionGuard() {}

    static boolean protectedForDemolish(ServerLevel level, BlockCoord at, String blockId, PlotPlanBlocks.Plan plan) {
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        List<String> ids = neighbours(level, pos);
        SettlementBreak.Facts facts = new SettlementBreak.Facts(
                SettlementBreak.Origin.UNKNOWN, true, plan.planned(at), blockId, false, false);
        BlockProtection.Decision decision = SettlementBreak.decide(PROTECTION, facts, () -> ids);
        return decision instanceof BlockProtection.Refused || BuildingSeeds.adjacentToStructure(ids);
    }

    static boolean protectedStray(ServerLevel level, BlockCoord at, String blockId) {
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        BlockProtection.Decision hard = PROTECTION.checkOwn(blockId, false);
        return hard instanceof BlockProtection.Refused || BuildingSeeds.adjacentToStructure(neighbours(level, pos));
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

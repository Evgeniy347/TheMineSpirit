package com.theminespirit.neoforge.policy;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.town.FenceDemolition;
import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.PlanBuildings;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.BuildPlanOwner;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.PlotBreakLog;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class PolicyExcavation {
    private PolicyExcavation() {}

    static @Nullable StageSite site(Level level) {
        return level instanceof ServerLevel server
                ? SettlementPlotStore.get(server).site().orElse(null)
                : null;
    }

    static boolean inFenceStrip(ServerLevel level, BlockCoord coord, String blockId) {
        return SettlementPlotStore.get(level)
                .site()
                .map(site -> FenceDemolition.allowed(blockId, site.fenceStrip(), coord.x(), coord.z()))
                .orElse(false);
    }

    static PlanBuilding planBuilding(ServerLevel level, BlockCoord coord) {
        return SettlementPlotStore.get(level)
                .site()
                .map(site -> site.protectionBuilding(coord.x(), coord.y(), coord.z()))
                .orElse(PlanBuilding.NONE);
    }

    static boolean foreignPlan(ServerLevel level, BlockPos pos, PlanBuilding ownBuilding) {
        return PlanBuildings.foreign(
                ownBuilding, planBuilding(level, new BlockCoord(pos.getX(), pos.getY(), pos.getZ())));
    }

    static BlockProtection.Decision demolish(ProtectionGate gate, SpiritEntity spirit, BlockPos pos, BlockState state) {
        PlanBuilding ownBuilding = BuildPlanOwner.of(spirit);
        if (spirit.level() instanceof ServerLevel server
                && PlacedBlocksData.get(server).isSpiritMarked(pos)
                && !foreignPlan(server, pos, ownBuilding)) {
            return new BlockProtection.Allowed();
        }
        return gate.decide(
                pos, state, false, true, ownBuilding, PlotProtection.Action.BREAK, PlotProtection.Ground.PLAN_DEMOLISH);
    }

    static BlockProtection.Decision excavate(ProtectionGate gate, SpiritEntity spirit, BlockPos pos, BlockState state) {
        if (spirit.level() instanceof ServerLevel server) {
            PlacedBlocksData placed = PlacedBlocksData.get(server);
            if (placed.isSpiritMarked(pos) || placed.isMarked(pos)) {
                PlotBreakLog.refused(
                        server,
                        pos,
                        spirit,
                        PlotProtection.Action.DIG.name(),
                        PlotProtection.Ground.PLAN_EXCAVATE,
                        "клетка " + pos.toShortString() + " уже облицована по плану: шахту копают до облицовки, "
                                + "готовый блок не выламывают");
                return new BlockProtection.Refused(
                        PolicyId.PROTECTED_BLOCKS,
                        "клетка " + pos.toShortString() + " уже облицована по плану: готовый блок шахты не выламывают");
            }
        }
        return gate.decide(
                pos,
                state,
                false,
                false,
                PlanBuilding.MINE,
                PlotProtection.Action.DIG,
                PlotProtection.Ground.PLAN_EXCAVATE);
    }
}

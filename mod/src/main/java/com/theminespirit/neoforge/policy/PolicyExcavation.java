package com.theminespirit.neoforge.policy;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.api.PolicyId;
import com.theminespirit.core.town.FenceDemolition;
import com.theminespirit.core.town.PlanBuildings;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.api.PlanBuilding;
import com.theminespirit.neoforge.BuildPlanOwner;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.ChestDemolition;
import com.theminespirit.neoforge.board.ChestTickets;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.lease.LeaseData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class PolicyExcavation {
    private PolicyExcavation() {}

    static @Nullable StageSite site(Level level) {
        return level instanceof ServerLevel server
                ? SettlementPlotStore.liveSite(server).orElse(null)
                : null;
    }

    static boolean inFenceStrip(ServerLevel level, BlockCoord coord, String blockId) {
        return SettlementPlotStore.liveSite(level)
                .map(site -> FenceDemolition.allowed(blockId, site.fenceStrip(), coord.x(), coord.z()))
                .orElse(false);
    }

    static PlanBuilding planBuilding(ServerLevel level, BlockCoord coord) {
        return SettlementPlotStore.liveSite(level)
                .map(site -> site.protectionBuilding(coord.x(), coord.y(), coord.z()))
                .orElse(PlanBuilding.NONE);
    }

    static boolean foreignPlan(ServerLevel level, BlockPos pos, PlanBuilding ownBuilding) {
        return PlanBuildings.foreign(
                ownBuilding, planBuilding(level, new BlockCoord(pos.getX(), pos.getY(), pos.getZ())));
    }

    static BlockProtection.Decision demolish(ProtectionGate gate, SpiritEntity spirit, BlockPos pos, BlockState state) {
        PlanBuilding ownBuilding = BuildPlanOwner.of(spirit);
        if (state.getBlock() instanceof ChestBlock && spirit.level() instanceof ServerLevel server) {
            BlockCoord cell = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
            if (!ChestDemolition.removable(server, cell)) {
                return new BlockProtection.Refused(
                        PolicyId.PROTECTED_BLOCKS,
                        "сундук в клетке плана склада/двора либо содержимое неизвестно — его переносит задание сноса");
            }
            if (ChestTickets.assigned(server, spirit.getUUID(), cell)) {
                return gate.decide(
                        pos,
                        state,
                        false,
                        true,
                        PlanBuilding.NONE,
                        PlotProtection.Action.BREAK,
                        PlotProtection.Ground.CHEST_SALVAGE);
            }
            PlanBuilding building = planBuilding(server, cell);
            if (building != PlanBuilding.NONE) {
                return gate.decide(
                        pos,
                        state,
                        false,
                        true,
                        building,
                        PlotProtection.Action.BREAK,
                        PlotProtection.Ground.PLAN_DEMOLISH);
            }
        }
        if (spirit.level() instanceof ServerLevel server
                && LeaseData.get(server).isActive(pos)
                && !foreignPlan(server, pos, ownBuilding)) {
            return new BlockProtection.Allowed();
        }
        return gate.decide(
                pos, state, false, true, ownBuilding, PlotProtection.Action.BREAK, PlotProtection.Ground.PLAN_DEMOLISH);
    }

    static BlockProtection.Decision excavate(ProtectionGate gate, SpiritEntity spirit, BlockPos pos, BlockState state) {
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

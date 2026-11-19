package com.theminespirit.neoforge.policy;

import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.BreakVeto;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.policy.SettlementBreak;
import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.board.PlotBreakLog;
import com.theminespirit.neoforge.board.PlotCellStructure;
import java.util.Collections;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class ProtectionDecide {
    record Request(
            BlockPos pos,
            BlockState state,
            boolean tended,
            boolean demolish,
            PlanBuilding ownBuilding,
            PlotProtection.Action action,
            PlotProtection.Ground ground) {}

    private record PlotOutcome(
            boolean inProtectedZone,
            boolean unprotectArea,
            boolean planCell,
            BlockProtection.@Nullable Decision refused) {}

    private final ProtectionGate gate;

    ProtectionDecide(ProtectionGate gate) {
        this.gate = gate;
    }

    BlockProtection.Decision decide(Request request) {
        gate.counted();
        BlockPos pos = request.pos();
        BlockCoord coord = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        BlockProtection.Decision outside = gate.checkTravel(coord);
        if (outside instanceof BlockProtection.Refused) {
            return outside;
        }
        Optional<String> carried = OtherSpiritSupport.carried(gate.spirit(), pos);
        if (carried.isPresent()) {
            return new BlockProtection.Refused(PolicyId.PROTECTED_ZONE, carried.orElseThrow());
        }
        Level level = gate.spirit().level();
        if (unprotectedTree(level, pos, request.state())) {
            return new BlockProtection.Allowed();
        }
        PlotOutcome plot = plotCheck(request, level, coord);
        if (plot.refused() != null) {
            return plot.refused();
        }
        BlockProtection.Decision decision = settlementDecision(request, level, coord, plot);
        if (decision instanceof BlockProtection.Refused) {
            return decision;
        }
        BlockPos feet = gate.spirit().blockPosition();
        BreakSafety veto = BreakVeto.checkBelowFeet(
                coord, new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), new LevelSafetyView(level));
        if (veto instanceof BreakSafety.Unsafe unsafe) {
            return new BlockProtection.Refused(PolicyId.DROP, unsafe.reason());
        }
        return decision;
    }

    private static boolean unprotectedTree(Level level, BlockPos pos, BlockState state) {
        if (!OrchardTrees.treePart(state) || !(level instanceof ServerLevel server)) {
            return false;
        }
        PlacedBlocksData placed = PlacedBlocksData.get(server);
        return PlotProtection.treeObstacle(true, placed.isMarked(pos) || placed.isSpiritMarked(pos));
    }

    private PlotOutcome plotCheck(Request request, Level level, BlockCoord coord) {
        BlockPos pos = request.pos();
        String dimension = level.dimension().location().toString();
        boolean inProtectedZone = PolicyConfig.zones().contains(dimension, coord);
        boolean unprotectArea = level instanceof ServerLevel server
                && UnprotectService.of(server.getServer()).covers(dimension, coord, System.currentTimeMillis());
        StageSite site = PolicyExcavation.site(level);
        PlotProtection.Ground effective = request.ground();
        if (site != null
                && request.tended()
                && site.protectionBuilding(coord.x(), coord.y(), coord.z()) == PlanBuilding.ORCHARD) {
            effective = PlotProtection.Ground.ORCHARD_CARE;
        }
        if (unprotectArea) {
            effective = PlotProtection.Ground.PLAYER_COMMAND;
        }
        boolean structure = level instanceof ServerLevel server && PlotCellStructure.isStructure(server, pos);
        PlotProtection.Decision plot =
                PlotProtection.check(site, coord, request.action(), effective, request.ownBuilding(), structure);
        if (plot instanceof PlotProtection.Refused refused) {
            if (gate.journaling() && level instanceof ServerLevel server) {
                PlotBreakLog.refused(
                        server, pos, gate.spirit(), request.action().name(), effective, refused.reason());
            }
            return new PlotOutcome(
                    inProtectedZone,
                    unprotectArea,
                    false,
                    new BlockProtection.Refused(PolicyId.PROTECTED_BLOCKS, refused.reason()));
        }
        boolean planCell = plot instanceof PlotProtection.Allowed allowed && allowed.building() != PlanBuilding.NONE;
        return new PlotOutcome(inProtectedZone, unprotectArea, planCell, null);
    }

    private BlockProtection.Decision settlementDecision(
            Request request, Level level, BlockCoord coord, PlotOutcome plot) {
        BlockPos pos = request.pos();
        BlockState state = request.state();
        boolean inProtectedZone = plot.inProtectedZone();
        boolean unprotectArea = plot.unprotectArea();
        if (request.demolish()
                && !inProtectedZone
                && level instanceof ServerLevel server
                && PolicyExcavation.inFenceStrip(server, coord, ProtectionGate.blockId(state))) {
            return new BlockProtection.Allowed();
        }
        if (plot.planCell()) {
            return new BlockProtection.Allowed();
        }
        if (request.tended()) {
            return ProtectionGate.PROTECTION.check(
                    ProtectionGate.blockId(state),
                    Collections.nCopies(BlockProtection.NEIGHBOUR_COUNT, ProtectionGate.AIR_ID),
                    new BlockProtection.Placement(false, inProtectedZone, unprotectArea));
        }
        PlotMarks.Marks marks = PlotMarks.of(level, pos, coord);
        SettlementBreak.Origin origin = marks.placedByPlayer()
                ? SettlementBreak.Origin.PLAYER
                : marks.spiritMarked() ? SettlementBreak.Origin.SPIRIT : SettlementBreak.Origin.UNKNOWN;
        return SettlementBreak.decide(
                ProtectionGate.PROTECTION,
                new SettlementBreak.Facts(
                        origin,
                        marks.onPlot(),
                        false,
                        ProtectionGate.blockId(state),
                        inProtectedZone,
                        unprotectArea,
                        PlanBuilding.NONE,
                        request.ownBuilding()),
                request.demolish() ? SettlementBreak.Intent.DEMOLISH : SettlementBreak.Intent.BREAK,
                () -> gate.neighbours().ids(level, pos));
    }
}

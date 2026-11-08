package com.theminespirit.neoforge.policy;

import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.BodyFlags;
import com.theminespirit.core.policy.BreakVeto;
import com.theminespirit.core.policy.LocalSurroundings;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.policy.PortalPolicy;
import com.theminespirit.core.policy.SelfRescue;
import com.theminespirit.core.policy.SettlementBreak;
import com.theminespirit.core.policy.WorkZone;
import com.theminespirit.core.policy.ZonePolicy;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.FenceDemolition;
import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.PlanBuildings;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.BuildPlanOwner;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.PlotBreakLog;
import com.theminespirit.neoforge.board.PlotCellStructure;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class ProtectionGate {
    private static final String NATURAL_STAND_IN = "minecraft:stone";
    private static final BlockProtection PROTECTION =
            DataTables.INSTANCE.protection().current();
    private static final String AIR_ID = "minecraft:air";

    private final SpiritEntity spirit;
    private int neighbourReads;
    private int checks;

    public ProtectionGate(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public BlockProtection.Decision checkBreak(BlockPos pos, BlockState state) {
        return decide(
                pos, state, false, false, PlanBuilding.NONE, PlotProtection.Action.BREAK, PlotProtection.Ground.NONE);
    }

    public BlockProtection.Decision checkBreakOrEscape(BlockPos pos, BlockState state, boolean escaping) {
        BlockPos feet = spirit.blockPosition();
        if (escaping
                && SelfRescue.mayBreakAtFeet(
                        new BlockCoord(pos.getX(), pos.getY(), pos.getZ()),
                        new BlockCoord(feet.getX(), feet.getY(), feet.getZ()))) {
            return new BlockProtection.Allowed();
        }
        return decide(
                pos, state, false, false, PlanBuilding.NONE, PlotProtection.Action.BREAK, PlotProtection.Ground.NONE);
    }

    public BlockProtection.Decision checkHarvest(BlockPos pos, BlockState state) {
        return decide(
                pos, state, true, false, PlanBuilding.NONE, PlotProtection.Action.HARVEST, PlotProtection.Ground.NONE);
    }

    public BlockProtection.Decision checkDemolish(BlockPos pos, BlockState state) {
        PlanBuilding ownBuilding = BuildPlanOwner.of(spirit);
        if (spirit.level() instanceof ServerLevel server
                && PlacedBlocksData.get(server).isSpiritMarked(pos)
                && !foreignPlan(server, pos, ownBuilding)) {
            return new BlockProtection.Allowed();
        }
        return decide(
                pos, state, false, true, ownBuilding, PlotProtection.Action.BREAK, PlotProtection.Ground.PLAN_DEMOLISH);
    }

    public BlockProtection.Decision checkRepair(BlockPos pos, BlockState state) {
        return decide(
                pos,
                state,
                false,
                true,
                BuildPlanOwner.of(spirit),
                PlotProtection.Action.BREAK,
                PlotProtection.Ground.REPAIR);
    }

    public BlockProtection.Decision checkExcavate(BlockPos pos, BlockState state) {
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
        return decide(
                pos,
                state,
                false,
                false,
                PlanBuilding.MINE,
                PlotProtection.Action.DIG,
                PlotProtection.Ground.PLAN_EXCAVATE);
    }

    private static @Nullable StageSite site(Level level) {
        return level instanceof ServerLevel server
                ? SettlementPlotStore.get(server).site().orElse(null)
                : null;
    }

    private static boolean inFenceStrip(ServerLevel level, BlockCoord coord, String blockId) {
        return SettlementPlotStore.get(level)
                .site()
                .map(site -> FenceDemolition.allowed(blockId, site.fenceStrip(), coord.x(), coord.z()))
                .orElse(false);
    }

    private static boolean onSettlementPlot(ServerLevel level, BlockCoord coord) {
        return SettlementPlotStore.get(level)
                .site()
                .map(site -> site.inside(coord.x(), coord.z()))
                .orElse(false);
    }

    private static PlanBuilding planBuilding(ServerLevel level, BlockCoord coord) {
        return SettlementPlotStore.get(level)
                .site()
                .map(site -> site.protectionBuilding(coord.x(), coord.y(), coord.z()))
                .orElse(PlanBuilding.NONE);
    }

    private static boolean foreignPlan(ServerLevel level, BlockPos pos, PlanBuilding ownBuilding) {
        return PlanBuildings.foreign(
                ownBuilding, planBuilding(level, new BlockCoord(pos.getX(), pos.getY(), pos.getZ())));
    }

    private BlockProtection.Decision decide(
            BlockPos pos,
            BlockState state,
            boolean tended,
            boolean demolish,
            PlanBuilding ownBuilding,
            PlotProtection.Action action,
            PlotProtection.Ground ground) {
        checks++;
        BlockCoord coord = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        BlockProtection.Decision outside = checkTravel(coord);
        if (outside instanceof BlockProtection.Refused) {
            return outside;
        }
        Level level = spirit.level();
        if (!tended && OrchardTrees.treePart(state) && OrchardTrees.planned(level, coord)) {
            return new BlockProtection.Refused(
                    PolicyId.PROTECTED_ZONE,
                    "в " + pos.toShortString() + " дерево или саженец сада плана: сад — часть плана, его не снимают "
                            + "снос, расчистка, выравнивание и фоновая рубка — только задание сада");
        }
        String dimension = level.dimension().location().toString();
        boolean inProtectedZone = PolicyConfig.zones().contains(dimension, coord);
        boolean unprotectArea = level instanceof ServerLevel server
                && UnprotectService.of(server.getServer()).covers(dimension, coord, System.currentTimeMillis());
        String id = blockId(state);
        StageSite site = site(level);
        PlotProtection.Ground effective = ground;
        if (site != null
                && tended
                && site.protectionBuilding(coord.x(), coord.y(), coord.z()) == PlanBuilding.ORCHARD) {
            effective = PlotProtection.Ground.ORCHARD_CARE;
        }
        if (unprotectArea) {
            effective = PlotProtection.Ground.PLAYER_COMMAND;
        }
        boolean structure = level instanceof ServerLevel server && PlotCellStructure.isStructure(server, pos);
        PlotProtection.Decision plot = PlotProtection.check(site, coord, action, effective, ownBuilding, structure);
        if (plot instanceof PlotProtection.Refused refused) {
            if (level instanceof ServerLevel server) {
                PlotBreakLog.refused(server, pos, spirit, action.name(), effective, refused.reason());
            }
            return new BlockProtection.Refused(PolicyId.PROTECTED_BLOCKS, refused.reason());
        }
        boolean planCell = plot instanceof PlotProtection.Allowed allowed && allowed.building() != PlanBuilding.NONE;
        BlockProtection.Decision decision;
        if (demolish && !inProtectedZone && level instanceof ServerLevel server && inFenceStrip(server, coord, id)) {
            decision = new BlockProtection.Allowed();
        } else if (planCell) {
            decision = new BlockProtection.Allowed();
        } else if (tended) {
            decision = PROTECTION.check(
                    blockId(state),
                    Collections.nCopies(BlockProtection.NEIGHBOUR_COUNT, AIR_ID),
                    new BlockProtection.Placement(false, inProtectedZone, unprotectArea));
        } else {
            boolean spiritMarked = false;
            boolean placedByPlayer = false;
            boolean onPlot = false;
            if (level instanceof ServerLevel server) {
                spiritMarked = PlacedBlocksData.get(server).isSpiritMarked(pos);
                placedByPlayer = !spiritMarked && PlacedBlocksData.get(server).isMarked(pos);
                onPlot = onSettlementPlot(server, coord);
            }
            SettlementBreak.Origin origin = placedByPlayer
                    ? SettlementBreak.Origin.PLAYER
                    : spiritMarked ? SettlementBreak.Origin.SPIRIT : SettlementBreak.Origin.UNKNOWN;
            decision = SettlementBreak.decide(
                    PROTECTION,
                    new SettlementBreak.Facts(
                            origin,
                            onPlot,
                            false,
                            blockId(state),
                            inProtectedZone,
                            unprotectArea,
                            PlanBuilding.NONE,
                            ownBuilding),
                    demolish ? SettlementBreak.Intent.DEMOLISH : SettlementBreak.Intent.BREAK,
                    () -> neighbours(level, pos));
        }
        if (decision instanceof BlockProtection.Refused) {
            return decision;
        }
        BlockPos feet = spirit.blockPosition();
        BreakSafety veto = BreakVeto.checkBelowFeet(
                coord, new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), new LevelSafetyView(level));
        if (veto instanceof BreakSafety.Unsafe unsafe) {
            return new BlockProtection.Refused(PolicyId.DROP, unsafe.reason());
        }
        return decision;
    }

    public BlockProtection.Decision checkPlace(BlockPos pos) {
        BlockCoord coord = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        BlockProtection.Decision outside = checkTravel(coord);
        if (outside instanceof BlockProtection.Refused) {
            return outside;
        }
        if (PolicyConfig.zones().contains(spirit.level().dimension().location().toString(), coord)) {
            return new BlockProtection.Refused(
                    PolicyId.PROTECTED_ZONE,
                    "позиция " + pos.toShortString() + " лежит в защищённой зоне: ставить там блоки нельзя");
        }
        return new BlockProtection.Allowed();
    }

    public BlockProtection.Decision checkTravel(BlockCoord target) {
        Optional<WorkZone> zone = spirit.policies().workZone();
        if (zone.isPresent() && ZonePolicy.checkTarget(target, zone.get()) instanceof ZonePolicy.Refused refused) {
            return new BlockProtection.Refused(PolicyId.WORK_ZONE, refused.reason());
        }
        return new BlockProtection.Allowed();
    }

    public BlockProtection.Decision checkUse(BlockPos pos) {
        BlockCoord coord = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        BlockProtection.Decision zone = checkTravel(coord);
        if (zone instanceof BlockProtection.Refused) {
            return zone;
        }
        Level level = spirit.level();
        if (level instanceof ServerLevel server && PlacedBlocksData.get(server).isSpiritMarked(pos)) {
            return new BlockProtection.Allowed();
        }
        String dimension = level.dimension().location().toString();
        boolean inProtectedZone = PolicyConfig.zones().contains(dimension, coord);
        boolean unprotectArea = level instanceof ServerLevel server
                && UnprotectService.of(server.getServer()).covers(dimension, coord, System.currentTimeMillis());
        boolean placedByPlayer = level instanceof ServerLevel server
                && PlacedBlocksData.get(server).isMarked(pos);
        return PROTECTION.checkUse(
                neighbours(level, pos), new BlockProtection.Placement(placedByPlayer, inProtectedZone, unprotectArea));
    }

    public BlockProtection.Decision checkPortalTarget(BlockCoord target) {
        LocalSurroundings around =
                LocalSurroundings.read(target, new LevelSafetyView(spirit.level()), BodyFlags.CALM, Optional.empty());
        if (PortalPolicy.vetoesTarget(around, target, spirit.policies().exemptions())) {
            return new BlockProtection.Refused(
                    PolicyId.PORTAL,
                    "цель (x=" + target.x() + ", y=" + target.y() + ", z=" + target.z()
                            + ") — клетка портала: без исключения PORTAL дух в порталы не заходит");
        }
        return new BlockProtection.Allowed();
    }

    public int neighbourReads() {
        return neighbourReads;
    }

    public int checks() {
        return checks;
    }

    private List<String> neighbours(Level level, BlockPos pos) {
        List<String> ids = new ArrayList<>(BlockProtection.NEIGHBOUR_COUNT);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        neighbourReads++;
                        BlockPos near = pos.offset(dx, dy, dz);
                        boolean spirits = level instanceof ServerLevel server
                                && PlacedBlocksData.get(server).isSpiritMarked(near);
                        ids.add(spirits ? NATURAL_STAND_IN : blockId(level.getBlockState(near)));
                    }
                }
            }
        }
        return ids;
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }
}

package com.theminespirit.neoforge.policy;

import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.BodyFlags;
import com.theminespirit.core.policy.BreakVeto;
import com.theminespirit.core.policy.LocalSurroundings;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.policy.PortalPolicy;
import com.theminespirit.core.policy.ProtectionConfig;
import com.theminespirit.core.policy.SettlementBreak;
import com.theminespirit.core.policy.WorkZone;
import com.theminespirit.core.policy.ZonePolicy;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
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

public final class ProtectionGate {
    private static final String NATURAL_STAND_IN = "minecraft:stone";
    private static final BlockProtection PROTECTION = ProtectionConfig.load();
    private static final String AIR_ID = "minecraft:air";

    private final SpiritEntity spirit;
    private int neighbourReads;
    private int checks;

    public ProtectionGate(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public BlockProtection.Decision checkBreak(BlockPos pos, BlockState state) {
        return decideBreak(pos, state, false);
    }

    public BlockProtection.Decision checkHarvest(BlockPos pos, BlockState state) {
        return decideBreak(pos, state, true);
    }

    public BlockProtection.Decision checkDemolish(BlockPos pos, BlockState state) {
        if (spirit.level() instanceof ServerLevel server
                && PlacedBlocksData.get(server).isSpiritMarked(pos)) {
            return new BlockProtection.Allowed();
        }
        return checkBreak(pos, state);
    }

    private static boolean inPlan(ServerLevel level, BlockCoord coord) {
        return SettlementPlotStore.get(level)
                .site()
                .map(site -> site.planCell(coord.x(), coord.y(), coord.z()))
                .orElse(false);
    }

    private static boolean onSettlementPlot(ServerLevel level, BlockCoord coord) {
        return SettlementPlotStore.get(level)
                .site()
                .map(site -> site.inside(coord.x(), coord.z()))
                .orElse(false);
    }

    private BlockProtection.Decision decideBreak(BlockPos pos, BlockState state, boolean tended) {
        checks++;
        BlockCoord coord = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        BlockProtection.Decision outside = checkTravel(coord);
        if (outside instanceof BlockProtection.Refused) {
            return outside;
        }
        Level level = spirit.level();
        if (!tended && GroveTrees.treePart(state) && GroveTrees.planned(level, coord)) {
            return new BlockProtection.Refused(
                    PolicyId.PROTECTED_ZONE,
                    "в " + pos.toShortString() + " дерево или саженец рощи плана: роща — часть плана, её не снимают "
                            + "снос, расчистка, выравнивание и фоновая рубка — только задание рощи");
        }
        String dimension = level.dimension().location().toString();
        boolean inProtectedZone = PolicyConfig.zones().contains(dimension, coord);
        boolean unprotectArea = level instanceof ServerLevel server
                && UnprotectService.of(server.getServer()).covers(dimension, coord, System.currentTimeMillis());
        BlockProtection.Decision decision;
        if (tended) {
            decision = PROTECTION.check(
                    blockId(state),
                    Collections.nCopies(BlockProtection.NEIGHBOUR_COUNT, AIR_ID),
                    new BlockProtection.Placement(false, inProtectedZone, unprotectArea));
        } else {
            boolean spiritMarked = false;
            boolean placedByPlayer = false;
            boolean onPlot = false;
            boolean inPlan = false;
            if (level instanceof ServerLevel server) {
                spiritMarked = PlacedBlocksData.get(server).isSpiritMarked(pos);
                placedByPlayer = !spiritMarked && PlacedBlocksData.get(server).isMarked(pos);
                onPlot = onSettlementPlot(server, coord);
                inPlan = onPlot && inPlan(server, coord);
            }
            SettlementBreak.Origin origin = placedByPlayer
                    ? SettlementBreak.Origin.PLAYER
                    : spiritMarked ? SettlementBreak.Origin.SPIRIT : SettlementBreak.Origin.UNKNOWN;
            SettlementBreak.Place place = inPlan
                    ? SettlementBreak.Place.PLAN
                    : onPlot ? SettlementBreak.Place.PLOT : SettlementBreak.Place.OFF_PLOT;
            decision = switch (SettlementBreak.of(origin, place)) {
                case SETTLEMENT -> PROTECTION.checkSettlement(blockId(state), inProtectedZone);
                case OWN -> PROTECTION.checkOwn(blockId(state), inProtectedZone);
                case DEFAULT ->
                    PROTECTION.check(
                            blockId(state),
                            neighbours(level, pos),
                            new BlockProtection.Placement(placedByPlayer, inProtectedZone, unprotectArea));
            };
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

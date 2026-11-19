package com.theminespirit.neoforge.policy;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.BodyFlags;
import com.theminespirit.core.policy.LocalSurroundings;
import com.theminespirit.core.policy.PortalPolicy;
import com.theminespirit.core.policy.SelfRescue;
import com.theminespirit.core.policy.WorkZone;
import com.theminespirit.core.policy.ZonePolicy;
import com.theminespirit.core.policy.api.PolicyId;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.BuildPlanOwner;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class ProtectionGate {
    static final BlockProtection PROTECTION = DataTables.INSTANCE.protection().current();
    static final String AIR_ID = "minecraft:air";

    private final SpiritEntity spirit;
    private final boolean journal;
    private final ProtectionNeighbours neighbours = new ProtectionNeighbours();
    private int checks;

    public ProtectionGate(SpiritEntity spirit) {
        this(spirit, true);
    }

    public ProtectionGate(SpiritEntity spirit, boolean journal) {
        this.spirit = spirit;
        this.journal = journal;
    }

    public BlockProtection.Decision checkBreak(BlockPos pos, BlockState state) {
        return decide(
                pos, state, false, false, PlanBuilding.NONE, PlotProtection.Action.BREAK, PlotProtection.Ground.NONE);
    }

    public BlockProtection.Decision checkBreakOrEscape(BlockPos pos, BlockState state, boolean escaping) {
        return checkBreakOrEscape(pos, state, escaping, false);
    }

    public BlockProtection.Decision checkBreakOrEscape(
            BlockPos pos, BlockState state, boolean escaping, boolean rescue) {
        if (escaping) {
            BlockProtection.Decision escape = escape(pos, state, rescue, true);
            if (escape != null) {
                return escape;
            }
        }
        return decide(
                pos, state, false, false, PlanBuilding.NONE, PlotProtection.Action.BREAK, PlotProtection.Ground.NONE);
    }

    public BlockProtection.Decision checkEscape(BlockPos pos, BlockState state, boolean rescue) {
        BlockProtection.Decision escape = escape(pos, state, rescue, false);
        if (escape != null) {
            return escape;
        }
        return decide(
                pos, state, false, false, PlanBuilding.NONE, PlotProtection.Action.BREAK, PlotProtection.Ground.NONE);
    }

    private BlockProtection.@Nullable Decision escape(BlockPos pos, BlockState state, boolean rescue, boolean record) {
        BlockPos feet = spirit.blockPosition();
        BlockCoord target = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        BlockCoord standing = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        if (rescue) {
            BlockProtection.Decision outside =
                    record ? RescueBreach.allow(spirit, pos, state) : RescueBreach.permit(spirit, pos, state);
            if (outside instanceof BlockProtection.Allowed) {
                return outside;
            }
            if (!RescueBreach.breakable(state)) {
                return new BlockProtection.Refused(
                        PolicyId.PROTECTED_ZONE,
                        "клетка " + pos.toShortString() + " — сундук, дверь или люк: для самоспасения не ломается");
            }
        }
        if (SelfRescue.mayBreakAtFeet(target, standing)) {
            return new BlockProtection.Allowed();
        }
        if (SelfRescue.mayBreakEscaping(target, standing) && RescueBreach.breakable(state)) {
            return new BlockProtection.Allowed();
        }
        return null;
    }

    public BlockProtection.Decision checkHarvest(BlockPos pos, BlockState state) {
        return decide(
                pos, state, true, false, PlanBuilding.NONE, PlotProtection.Action.HARVEST, PlotProtection.Ground.NONE);
    }

    public BlockProtection.Decision checkDemolish(BlockPos pos, BlockState state) {
        return PolicyExcavation.demolish(this, spirit, pos, state);
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
        return PolicyExcavation.excavate(this, spirit, pos, state);
    }

    BlockProtection.Decision decide(
            BlockPos pos,
            BlockState state,
            boolean tended,
            boolean demolish,
            PlanBuilding ownBuilding,
            PlotProtection.Action action,
            PlotProtection.Ground ground) {
        return new ProtectionDecide(this)
                .decide(new ProtectionDecide.Request(pos, state, tended, demolish, ownBuilding, action, ground));
    }

    SpiritEntity spirit() {
        return spirit;
    }

    boolean journaling() {
        return journal;
    }

    ProtectionNeighbours neighbours() {
        return neighbours;
    }

    void counted() {
        checks++;
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
                neighbours.ids(level, pos),
                new BlockProtection.Placement(placedByPlayer, inProtectedZone, unprotectArea));
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

    public int checks() {
        return checks;
    }

    static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }
}

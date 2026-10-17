package com.theminespirit.neoforge.policy;

import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.BodyFlags;
import com.theminespirit.core.policy.BreakVeto;
import com.theminespirit.core.policy.LocalSurroundings;
import com.theminespirit.core.policy.PortalPolicy;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.policy.ProtectionConfig;
import com.theminespirit.core.policy.WorkZone;
import com.theminespirit.core.policy.ZonePolicy;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
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

    private BlockProtection.Decision decideBreak(BlockPos pos, BlockState state, boolean tended) {
        checks++;
        BlockCoord coord = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        BlockProtection.Decision outside = checkTravel(coord);
        if (outside instanceof BlockProtection.Refused) {
            return outside;
        }
        Level level = spirit.level();
        String dimension = level.dimension().location().toString();
        boolean inProtectedZone = PolicyConfig.zones().contains(dimension, coord);
        boolean unprotectArea = level instanceof ServerLevel server
                && UnprotectService.of(server.getServer()).covers(dimension, coord, System.currentTimeMillis());
        BlockProtection.Decision decision;
        if (tended) {
            decision = PROTECTION.check(blockId(state), Collections.nCopies(BlockProtection.NEIGHBOUR_COUNT, AIR_ID), false,
                    inProtectedZone, unprotectArea);
        } else if (level instanceof ServerLevel server && PlacedBlocksData.get(server).isSpiritMarked(pos)) {
            decision = PROTECTION.checkOwn(blockId(state), inProtectedZone);
        } else {
            boolean placedByPlayer = level instanceof ServerLevel server && PlacedBlocksData.get(server).isMarked(pos);
            decision = PROTECTION.check(blockId(state), neighbours(level, pos), placedByPlayer, inProtectedZone, unprotectArea);
        }
        if (decision instanceof BlockProtection.Refused) {
            return decision;
        }
        BlockPos feet = spirit.blockPosition();
        BreakSafety veto = BreakVeto.checkBelowFeet(coord, new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), new LevelSafetyView(level));
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
            return new BlockProtection.Refused(PolicyId.PROTECTED_ZONE, "позиция " + pos.toShortString() + " лежит в защищённой зоне: ставить там блоки нельзя");
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

    public BlockProtection.Decision checkPortalTarget(BlockCoord target) {
        LocalSurroundings around = LocalSurroundings.read(target, new LevelSafetyView(spirit.level()), BodyFlags.CALM, Optional.empty());
        if (PortalPolicy.vetoesTarget(around, target, spirit.policies().exemptions())) {
            return new BlockProtection.Refused(PolicyId.PORTAL, "цель (x=" + target.x() + ", y=" + target.y() + ", z=" + target.z()
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
                        ids.add(blockId(level.getBlockState(pos.offset(dx, dy, dz))));
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

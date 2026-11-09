package com.theminespirit.neoforge;

import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageStands;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

final class FillStands {
    private final SpiritEntity spirit;
    private final Level level;
    private final String blockId;
    private final Set<BlockCoord> region;
    private final SafetyView safety;

    FillStands(SpiritEntity spirit, Level level, String blockId, Set<BlockCoord> region, SafetyView safety) {
        this.spirit = spirit;
        this.level = level;
        this.blockId = blockId;
        this.region = region;
        this.safety = safety;
    }

    boolean insideBox(BlockPos pos) {
        return region.contains(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
    }

    BlockCoord exitStand() {
        BlockPos feet = spirit.blockPosition();
        BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        List<BlockCoord> candidates = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockCoord cell = new BlockCoord(from.x() + dx, from.y() + dy, from.z() + dz);
                    if (region.contains(cell)) {
                        continue;
                    }
                    if (MiningSafety.canStand(cell, safety)) {
                        candidates.add(cell);
                    }
                }
            }
        }
        candidates.sort((first, second) -> Long.compare(distanceSquared(from, first), distanceSquared(from, second)));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    PillarColumn pillarHere(BlockCoord cell) {
        return level.getBlockState(toPos(cell)).getFluidState().is(FluidTags.WATER)
                ? PillarColumn.throughWater(spirit, blockId)
                : new PillarColumn(spirit, blockId, 1);
    }

    boolean inReach(BlockCoord cell) {
        BlockPos feet = spirit.blockPosition();
        BlockPos pos = toPos(cell);
        return (spirit.onGround() || spirit.isInWater())
                && !pos.equals(feet)
                && !pos.equals(feet.above())
                && SpiritReach.reachable(new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), cell);
    }

    boolean floorUnder(BlockCoord cell) {
        BlockPos pos = toPos(cell);
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above())
                        .getCollisionShape(level, pos.above())
                        .isEmpty();
    }

    boolean allowedStand(BlockCoord stand) {
        if (!(level instanceof ServerLevel server)) {
            return true;
        }
        boolean sky = openAbove(stand);
        return SettlementPlotStore.liveSite(server)
                .map(site -> StageStands.fillStandAllowed(site, stand, sky))
                .orElse(true);
    }

    private boolean openAbove(BlockCoord stand) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, stand.x(), stand.z());
        for (int y = stand.y() + 1; y < top; y++) {
            BlockPos pos = new BlockPos(stand.x(), y, stand.z());
            BlockState current = level.getBlockState(pos);
            if (!current.getCollisionShape(level, pos).isEmpty() && !(current.getBlock() instanceof LeavesBlock)) {
                return false;
            }
        }
        return true;
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}

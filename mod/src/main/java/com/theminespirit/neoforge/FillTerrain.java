package com.theminespirit.neoforge;

import com.theminespirit.core.building.FillOrder;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

final class FillTerrain implements FillOrder.World {
    private final Level level;
    private final SpiritEntity spirit;
    private final BlockState wanted;
    private final Set<BlockCoord> region;
    private int blockReads;

    FillTerrain(Level level, SpiritEntity spirit, BlockState wanted, Set<BlockCoord> region) {
        this.level = level;
        this.spirit = spirit;
        this.wanted = wanted;
        this.region = region;
    }

    FillOrder.Plan plan(BlockCoord anchor) {
        return FillOrder.fill(List.copyOf(activeRegion()), occupiedByOthers(), this, anchor);
    }

    @Override
    public boolean solid(BlockCoord at) {
        BlockState state = level.getBlockState(toPos(at));
        blockReads++;
        if (state.is(wanted.getBlock())) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return !state.isAir() && !state.canBeReplaced();
    }

    @Override
    public boolean fluid(BlockCoord at) {
        blockReads++;
        return !level.getBlockState(toPos(at)).getFluidState().isEmpty();
    }

    boolean complete() {
        return FillOrder.filled(activeRegion(), this);
    }

    private Set<BlockCoord> activeRegion() {
        StageSite site = level instanceof ServerLevel server
                ? SettlementPlotStore.get(server).site().orElse(null)
                : null;
        if (site == null) {
            return region;
        }
        return region.stream()
                .filter(cell -> !site.protectionCell(cell.x(), cell.y(), cell.z()))
                .collect(Collectors.toUnmodifiableSet());
    }

    void countRead() {
        blockReads++;
    }

    int blockReads() {
        return blockReads;
    }

    private Set<BlockCoord> occupiedByOthers() {
        Set<BlockCoord> cells = new HashSet<>();
        for (SpiritEntity other : level.getEntitiesOfClass(SpiritEntity.class, regionBox())) {
            if (other.getUUID().equals(spirit.getUUID()) || other.isRemoved()) {
                continue;
            }
            BlockPos feet = other.blockPosition();
            BlockCoord legs = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
            BlockCoord head = new BlockCoord(feet.getX(), feet.getY() + 1, feet.getZ());
            if (region.contains(legs)) {
                cells.add(legs);
            }
            if (region.contains(head)) {
                cells.add(head);
            }
        }
        return cells;
    }

    private AABB regionBox() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockCoord cell : region) {
            minX = Math.min(minX, cell.x());
            minY = Math.min(minY, cell.y());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxY = Math.max(maxY, cell.y());
            maxZ = Math.max(maxZ, cell.z());
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}

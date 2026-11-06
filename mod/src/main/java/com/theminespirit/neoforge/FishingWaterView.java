package com.theminespirit.neoforge;

import com.theminespirit.core.fishing.FishingTuning;
import com.theminespirit.core.fishing.WaterView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

final class FishingWaterView implements WaterView {
    private final ServerLevel level;
    private final BlockCoord origin;
    private final List<int[]> offsets;
    private final Map<Long, Column> columns = new LinkedHashMap<>();
    private int processed;

    FishingWaterView(ServerLevel level, BlockCoord origin, int radius) {
        this.level = level;
        this.origin = origin;
        this.offsets = offsets(radius);
    }

    void advance() {
        int budget = FishingTuning.COLUMNS_PER_TICK;
        while (processed < offsets.size() && budget-- > 0) {
            int[] offset = offsets.get(processed++);
            int x = origin.x() + offset[0];
            int z = origin.z() + offset[1];
            readColumn(x, z).ifPresent(column -> columns.put(key(x, z), column));
        }
    }

    boolean done() {
        return processed >= offsets.size();
    }

    @Override
    public Collection<Column> columns() {
        return columns.values();
    }

    @Override
    public boolean canStand(BlockCoord feet) {
        BlockPos pos = new BlockPos(feet.x(), feet.y(), feet.z());
        BlockPos floor = pos.below();
        if (level.getFluidState(floor).is(FluidTags.WATER)) {
            return false;
        }
        if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
            return false;
        }
        return passable(pos) && passable(pos.above());
    }

    private boolean passable(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && state.getCollisionShape(level, pos).isEmpty();
    }

    private Optional<Column> readColumn(int x, int z) {
        int top = origin.y() + FishingTuning.VERTICAL_UP;
        int bottom = origin.y() - FishingTuning.VERTICAL_DOWN;
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getFluidState(pos).is(FluidTags.WATER)) {
                continue;
            }
            if (!level.getBlockState(pos.above()).isAir()) {
                return Optional.empty();
            }
            return Optional.of(new Column(x, z, y, depthAt(pos)));
        }
        return Optional.empty();
    }

    private int depthAt(BlockPos surface) {
        int depth = 0;
        BlockPos cursor = surface;
        while (level.getFluidState(cursor).is(FluidTags.WATER)) {
            depth++;
            cursor = cursor.below();
        }
        return depth;
    }

    private static List<int[]> offsets(int radius) {
        List<int[]> result = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    result.add(new int[] {dx, dz});
                }
            }
        }
        result.sort(Comparator.<int[]>comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1])
                .thenComparingInt(offset -> offset[0])
                .thenComparingInt(offset -> offset[1]));
        return result;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}

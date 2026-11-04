package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

final class MoreScan {
    private static final int DARK_LEVEL = 8;

    private MoreScan() {}

    static BlockPos pos(BlockCoord at) {
        return new BlockPos(at.x(), at.y(), at.z());
    }

    static BlockCoord coord(BlockPos at) {
        return new BlockCoord(at.getX(), at.getY(), at.getZ());
    }

    static Optional<BlockCoord> find(
            ServerLevel level,
            BlockCoord center,
            int radius,
            int below,
            int above,
            BiPredicate<BlockPos, BlockState> match) {
        BlockPos at = pos(center);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -below; dy <= above; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = at.offset(dx, dy, dz);
                    if (level.isLoaded(pos) && match.test(pos, level.getBlockState(pos))) {
                        return Optional.of(coord(pos));
                    }
                }
            }
        }
        return Optional.empty();
    }

    static List<BlockCoord> around(ServerLevel level, BlockCoord center, int radius, Predicate<BlockState> match) {
        Set<BlockCoord> found = new LinkedHashSet<>();
        BlockPos at = pos(center);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = at.offset(dx, dy, dz);
                    if (level.isLoaded(pos) && match.test(level.getBlockState(pos))) {
                        found.add(coord(pos));
                    }
                }
            }
        }
        return new ArrayList<>(found);
    }

    static @Nullable BlockCoord darkest(ServerLevel level, Iterable<ColumnMask.Cell> cells) {
        BlockCoord best = null;
        int darkest = Integer.MAX_VALUE;
        for (ColumnMask.Cell cell : cells) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.x(), cell.z());
            BlockPos at = new BlockPos(cell.x(), y, cell.z());
            if (!level.isLoaded(at)) {
                continue;
            }
            int light = level.getMaxLocalRawBrightness(at);
            if (light < DARK_LEVEL && light < darkest) {
                darkest = light;
                best = new BlockCoord(cell.x(), y, cell.z());
            }
        }
        return best;
    }
}

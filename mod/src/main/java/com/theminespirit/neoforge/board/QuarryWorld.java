package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.route.QuarryArea;
import com.theminespirit.core.board.route.QuarryProbe;
import com.theminespirit.core.board.route.RouteDigging;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

final class QuarryWorld implements QuarryProbe {
    private static final int WATER_PROBE = 6;
    private static final int WATER_SCAN_RADIUS = 2;

    private final ServerLevel level;
    private final QuarryArea area;
    private final @Nullable StageSite site;
    private final int waterLevel;

    private QuarryWorld(ServerLevel level, QuarryArea area, @Nullable StageSite site) {
        this.level = level;
        this.area = area;
        this.site = site;
        this.waterLevel = waterLevel(level, area.point());
    }

    static QuarryWorld of(ServerLevel level, QuarryArea area, @Nullable StageSite site) {
        return new QuarryWorld(level, area, site);
    }

    static void load(ServerLevel level, QuarryArea area) {
        int radius = area.radius();
        int minChunkX = (area.point().x() - radius) >> 4;
        int maxChunkX = (area.point().x() + radius) >> 4;
        int minChunkZ = (area.point().z() - radius) >> 4;
        int maxChunkZ = (area.point().z() + radius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    @Override
    public boolean loaded(int x, int z) {
        return level.isLoaded(new BlockPos(x, area.point().y(), z));
    }

    @Override
    public int top(int x, int z) {
        if (!loaded(x, z)) {
            return Integer.MIN_VALUE;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    @Override
    public String block(int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    @Override
    public int waterLevel() {
        return waterLevel;
    }

    @Override
    public int waterAround(int x, int y, int z) {
        int count = 0;
        for (int dx = -WATER_SCAN_RADIUS; dx <= WATER_SCAN_RADIUS; dx++) {
            for (int dy = -WATER_SCAN_RADIUS; dy <= WATER_SCAN_RADIUS; dy++) {
                for (int dz = -WATER_SCAN_RADIUS; dz <= WATER_SCAN_RADIUS; dz++) {
                    BlockPos at = new BlockPos(x + dx, y + dy, z + dz);
                    if (level.isLoaded(at)
                            && level.getBlockState(at).getFluidState().is(FluidTags.WATER)) {
                        count++;
                        if (count > RouteDigging.MAX_WATER) {
                            return count;
                        }
                    }
                }
            }
        }
        return count;
    }

    @Override
    public boolean forbidden(int x, int y, int z) {
        StageSite stage = site;
        if (stage == null) {
            return false;
        }
        BlockCoord at = new BlockCoord(x, y, z);
        return stage.inside(x, z) || stage.protectionCell(x, y, z) || stage.gatherForbidden(at, 0);
    }

    private static int waterLevel(ServerLevel level, BlockCoord point) {
        int water = NO_WATER;
        for (int dx = -WATER_PROBE; dx <= WATER_PROBE; dx++) {
            for (int dz = -WATER_PROBE; dz <= WATER_PROBE; dz++) {
                for (int y = point.y() + WATER_PROBE; y >= point.y() - WATER_PROBE; y--) {
                    BlockPos at = new BlockPos(point.x() + dx, y, point.z() + dz);
                    if (level.isLoaded(at)
                            && level.getBlockState(at).getFluidState().is(FluidTags.WATER)) {
                        water = Math.max(water, y);
                        break;
                    }
                }
            }
        }
        return water;
    }
}

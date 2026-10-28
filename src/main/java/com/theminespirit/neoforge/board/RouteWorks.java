package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.board.route.RouteDigging;
import com.theminespirit.core.board.route.RoutePath;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

public final class RouteWorks {
    private static final int DIG_RADIUS = 4;
    private static final int WATER_SCAN_RADIUS = 2;
    private static final int WATER_LEVEL_RADIUS = 6;

    private RouteWorks() {}

    public static PlotLeveling path(ServerLevel level, BlockCoord from, BlockCoord to, String block) {
        List<PlotLeveling.Box> cuts = new ArrayList<>();
        List<PlotLeveling.Box> fills = new ArrayList<>();
        for (BlockCoord tile : RoutePath.tiles(from, to)) {
            int x = tile.x();
            int z = tile.z();
            if (!level.isLoaded(new BlockPos(x, from.y(), z))) {
                continue;
            }
            int feet = GroundProbe.feet(level, x, z, from.y());
            int ground = feet - 1;
            if (level.getBlockState(new BlockPos(x, ground, z)).getFluidState().is(FluidTags.WATER)) {
                continue;
            }
            cuts.add(new PlotLeveling.Box(x, feet, z, x, feet, z));
            fills.add(new PlotLeveling.Box(x, feet, z, x, feet, z));
        }
        return new PlotLeveling(cuts, fills, block);
    }

    public static PlotLeveling dig(ServerLevel level, BlockCoord point) {
        ColumnMask area = ColumnMask.rect(
                point.x() - DIG_RADIUS, point.z() - DIG_RADIUS, point.x() + DIG_RADIUS, point.z() + DIG_RADIUS);
        int waterLevel = waterLevel(level, point);
        RouteDigging.WaterAround water = (x, y, z) -> waterAround(level, x, y, z);
        return RouteDigging.plan(area, (x, z) -> GroundProbe.feet(level, x, z, point.y() + 1) - 1, water, waterLevel);
    }

    private static int waterLevel(ServerLevel world, BlockCoord point) {
        int water = Integer.MIN_VALUE;
        for (int dx = -WATER_LEVEL_RADIUS; dx <= WATER_LEVEL_RADIUS; dx++) {
            for (int dz = -WATER_LEVEL_RADIUS; dz <= WATER_LEVEL_RADIUS; dz++) {
                for (int y = point.y() + WATER_LEVEL_RADIUS; y >= point.y() - WATER_LEVEL_RADIUS; y--) {
                    BlockPos at = new BlockPos(point.x() + dx, y, point.z() + dz);
                    if (world.isLoaded(at)
                            && world.getBlockState(at).getFluidState().is(FluidTags.WATER)) {
                        water = Math.max(water, y);
                        break;
                    }
                }
            }
        }
        return water;
    }

    private static int waterAround(ServerLevel level, int x, int y, int z) {
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
}

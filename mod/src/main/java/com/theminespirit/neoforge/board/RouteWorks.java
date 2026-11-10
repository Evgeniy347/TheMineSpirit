package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.route.QuarryArea;
import com.theminespirit.core.board.route.QuarryPlanner;
import com.theminespirit.core.board.route.RoutePath;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

public final class RouteWorks {
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

    public static int standY(ServerLevel level, int x, int z, int nearY) {
        return GroundProbe.feet(level, x, z, nearY);
    }

    public static PlotLeveling dig(ServerLevel level, BlockCoord point, RouteResource resource) {
        QuarryArea area = QuarryArea.at(resource, point);
        return QuarryPlanner.plan(
                        area,
                        QuarryWorld.of(
                                level, area, SettlementPlotStore.liveSite(level).orElse(null)))
                .leveling();
    }
}

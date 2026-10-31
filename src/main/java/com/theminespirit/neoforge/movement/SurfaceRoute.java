package com.theminespirit.neoforge.movement;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.movement.SurfaceEscape;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.Route;
import com.theminespirit.core.route.RouteStep;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public final class SurfaceRoute {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SurfaceRoute() {}

    public static Optional<RouteWalk> plan(SpiritEntity spirit) {
        BlockPos feet = spirit.blockPosition();
        BlockCoord start = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        Level level = spirit.level();
        Optional<List<BlockCoord>> found = SurfaceEscape.path(
                start,
                new RouteLevelView(spirit),
                cell -> DigOutScript.open(level, new BlockPos(cell.x(), cell.y(), cell.z())));
        if (found.isEmpty()) {
            LOGGER.info("[dig_out] дух {}: пути к небу из {} нет — копаю ступенями", spirit.displayName(), start);
            return Optional.empty();
        }
        List<BlockCoord> cells = found.orElseThrow();
        List<RouteStep> steps = new ArrayList<>(cells.size());
        for (BlockCoord cell : cells) {
            boolean water = level.getFluidState(new BlockPos(cell.x(), cell.y(), cell.z()))
                    .is(FluidTags.WATER);
            steps.add(new RouteStep(water ? RouteStep.Action.SWIM : RouteStep.Action.WALK, cell));
        }
        BlockCoord end = cells.get(cells.size() - 1);
        LOGGER.info(
                "[dig_out] дух {}: найден путь к небу из {} — клеток {}, конец {}",
                spirit.displayName(),
                start,
                cells.size(),
                end);
        Route route = new Route(Route.Status.FOUND, steps, cells.size() * 10.0, end, cells.size(), 0, 0, List.of());
        return Optional.of(new RouteWalk(spirit, route, end));
    }
}

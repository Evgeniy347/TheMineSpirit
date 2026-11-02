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
        return Optional.of(route(spirit, level, found.orElseThrow(), "к небу", 0));
    }

    public static Optional<RouteWalk> planDescent(SpiritEntity spirit, BlockCoord target) {
        BlockPos feet = spirit.blockPosition();
        BlockCoord start = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        Level level = spirit.level();
        Optional<List<BlockCoord>> found = SurfaceEscape.path(
                start,
                new RouteLevelView(spirit),
                cell -> Math.abs(cell.x() - target.x()) <= 2
                        && Math.abs(cell.z() - target.z()) <= 2
                        && cell.y() >= target.y() - 2
                        && cell.y() <= target.y() + 2,
                target);
        if (found.isEmpty()) {
            LOGGER.info("[dig_out] дух {}: пути вниз из {} к {} нет", spirit.displayName(), start, target);
            return Optional.empty();
        }
        return Optional.of(route(spirit, level, found.orElseThrow(), "вниз", 0));
    }

    public static Optional<RouteWalk> planReturn(SpiritEntity spirit, BlockCoord target) {
        BlockPos feet = spirit.blockPosition();
        BlockCoord start = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        Level level = spirit.level();
        RouteLevelView view = new RouteLevelView(spirit);
        long started = System.nanoTime();
        Optional<List<BlockCoord>> found = SurfaceEscape.path(
                start,
                view,
                cell -> Math.abs(cell.x() - target.x()) <= 2
                        && Math.abs(cell.z() - target.z()) <= 2
                        && cell.y() >= target.y() - 2
                        && cell.y() <= target.y() + 2,
                target);
        if (found.isEmpty()) {
            LOGGER.info("[dig_out] дух {}: пути домой из {} к {} нет", spirit.displayName(), start, target);
            return Optional.empty();
        }
        return Optional.of(route(spirit, level, found.orElseThrow(), "домой", started));
    }

    private static RouteWalk route(
            SpiritEntity spirit, Level level, List<BlockCoord> cells, String what, long started) {
        List<RouteStep> steps = new ArrayList<>(cells.size());
        for (BlockCoord cell : cells) {
            boolean water = level.getFluidState(new BlockPos(cell.x(), cell.y(), cell.z()))
                            .is(FluidTags.WATER)
                    || level.getFluidState(new BlockPos(cell.x(), cell.y() - 1, cell.z()))
                            .is(FluidTags.WATER);
            steps.add(new RouteStep(water ? RouteStep.Action.SWIM : RouteStep.Action.WALK, cell));
        }
        BlockCoord end = cells.get(cells.size() - 1);
        LOGGER.info(
                "[dig_out] дух {}: найден путь {} — клеток {}, конец {}, поиск {} мс",
                spirit.displayName(),
                what,
                cells.size(),
                end,
                Math.max(0, (System.nanoTime() - started) / 1_000_000));
        Route route = new Route(Route.Status.FOUND, steps, cells.size() * 10.0, end, cells.size(), 0, 0, List.of());
        return new RouteWalk(spirit, route, end);
    }
}

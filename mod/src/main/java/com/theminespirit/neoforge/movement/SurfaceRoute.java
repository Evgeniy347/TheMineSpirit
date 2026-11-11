package com.theminespirit.neoforge.movement;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.movement.DescentPath;
import com.theminespirit.core.movement.DigOutGoal;
import com.theminespirit.core.movement.SearchMillis;
import com.theminespirit.core.movement.SurfaceEscape;
import com.theminespirit.core.movement.SurfaceEscapeSession;
import com.theminespirit.core.movement.SurfaceGoal;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.Route;
import com.theminespirit.core.route.RouteStep;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class SurfaceRoute {
    public static final long SLICE_MILLIS = 5;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static long maxSliceNanos;

    public static long maxSliceMillis() {
        return maxSliceNanos / 1_000_000L;
    }

    public static void resetSliceMeter() {
        maxSliceNanos = 0;
    }

    public static final class Pending {
        private final Level level;
        private final SurfaceEscapeSession session;
        private final BlockCoord start;
        private final BlockCoord target;
        private final boolean descent;
        private final boolean rescue;
        private final long started;
        private boolean done;
        private @Nullable RouteWalk walk;

        private Pending(
                Level level,
                SurfaceEscapeSession session,
                BlockCoord start,
                BlockCoord target,
                boolean descent,
                boolean rescue) {
            this.level = level;
            this.session = session;
            this.start = start;
            this.target = target;
            this.descent = descent;
            this.rescue = rescue;
            this.started = System.nanoTime();
        }

        public boolean done() {
            return done;
        }

        public @Nullable RouteWalk walk() {
            return walk;
        }
    }

    private SurfaceRoute() {}

    public static Pending begin(SpiritEntity spirit, BlockCoord target, boolean descent, boolean rescue) {
        BlockPos feet = spirit.blockPosition();
        BlockCoord start = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        Level level = spirit.level();
        RouteLevelView view = new RouteLevelView(spirit, rescue);
        Predicate<BlockCoord> blocked = cell -> PlanDetour.foreign(spirit, cell);
        Predicate<BlockCoord> home = cell -> SurfaceGoal.home(cell, target, DigOutGoal.HOME_RADIUS);
        SurfaceEscapeSession session = descent
                ? new SurfaceEscapeSession(start, view, home, target, blocked, null)
                : SurfaceEscape.toHome(
                        start,
                        view,
                        target,
                        DigOutGoal.HOME_RADIUS,
                        blocked,
                        cell -> DigOutScript.open(level, new BlockPos(cell.x(), cell.y(), cell.z())));
        return new Pending(level, session, start, target, descent, rescue);
    }

    public static void advance(SpiritEntity spirit, Pending pending, long budgetMillis) {
        if (pending.done) {
            return;
        }
        SurfaceEscapeSession.Result result;
        long sliceStarted = System.nanoTime();
        try {
            result = pending.session.step(budgetMillis * 1_000_000L);
        } finally {
            maxSliceNanos = Math.max(maxSliceNanos, System.nanoTime() - sliceStarted);
        }
        if (result == null) {
            return;
        }
        pending.done = true;
        if (!result.found()) {
            if (pending.descent) {
                LOGGER.info(
                        "[dig_out] дух {}: пути вниз из {} к {} нет",
                        spirit.displayName(),
                        pending.start,
                        pending.target);
            } else {
                LOGGER.info(
                        "[dig_out] дух {}: пути к дому из {} нет — копаю ступенями",
                        spirit.displayName(),
                        pending.start);
            }
            return;
        }
        List<BlockCoord> cells = result.path();
        if (pending.descent) {
            String refusal = DescentPath.refusalOrNull(cells, water(pending.level, cells.get(cells.size() - 1)));
            if (refusal != null) {
                LOGGER.info(
                        "[dig_out] дух {}: путь вниз из {} к {} отвергнут: {} ({} клеток, конец {})",
                        spirit.displayName(),
                        pending.start,
                        pending.target,
                        refusal,
                        cells.size(),
                        cells.get(cells.size() - 1));
                return;
            }
        }
        String what = pending.descent ? "вниз" : result.primary() ? "к дому" : "к небу";
        pending.walk = route(spirit, pending.level, cells, what, pending.started, pending.rescue);
    }

    private static boolean water(Level level, BlockCoord cell) {
        return level.getFluidState(new BlockPos(cell.x(), cell.y(), cell.z())).is(FluidTags.WATER)
                || level.getFluidState(new BlockPos(cell.x(), cell.y() - 1, cell.z()))
                        .is(FluidTags.WATER);
    }

    private static RouteWalk route(
            SpiritEntity spirit, Level level, List<BlockCoord> cells, String what, long started, boolean rescue) {
        List<RouteStep> steps = new ArrayList<>(cells.size());
        for (BlockCoord cell : cells) {
            boolean water = water(level, cell);
            steps.add(new RouteStep(water ? RouteStep.Action.SWIM : RouteStep.Action.WALK, cell));
        }
        BlockCoord end = cells.get(cells.size() - 1);
        LOGGER.info(
                "[dig_out] дух {}: найден путь {} — клеток {}, конец {}, поиск {} мс",
                spirit.displayName(),
                what,
                cells.size(),
                end,
                SearchMillis.since(started, System.nanoTime()));
        Route route = new Route(Route.Status.FOUND, steps, cells.size() * 10.0, end, cells.size(), 0, 0, List.of());
        return new RouteWalk(spirit, route, end, rescue);
    }
}

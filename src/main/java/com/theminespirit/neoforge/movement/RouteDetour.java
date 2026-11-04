package com.theminespirit.neoforge.movement;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.navigation.NavCacheWorld;
import com.theminespirit.core.navigation.NavCachedChunk;
import com.theminespirit.core.navigation.NavCachedWorld;
import com.theminespirit.core.navigation.NavContext;
import com.theminespirit.core.navigation.NavGoalNear;
import com.theminespirit.core.navigation.NavRouteSteps;
import com.theminespirit.core.navigation.NavSegmentedRoute;
import com.theminespirit.core.navigation.NavSegmentedSession;
import com.theminespirit.core.navigation.NavSettings;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.Route;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteFailures;
import com.theminespirit.core.route.RouteThrottle;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.TickWatch;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class RouteDetour {
    public static final long SLICE_MILLIS = 10;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RouteThrottle THROTTLE = new RouteThrottle();
    private static final RouteFailures FAILURES = new RouteFailures();
    private static final long PLAN_TOTAL_MILLIS = 15000;
    private static final long FAIL_TTL_TICKS = 1200;
    private static final int MAX_SEGMENTS = 32;
    private static final int MIN_CUTOFF = 6;
    private static final double CUTOFF_FACTOR = 0.5;

    private record Plan(
            SpiritEntity spirit,
            BlockCoord target,
            String why,
            boolean rescue,
            BlockCoord from,
            RouteFailures.Key key,
            long now,
            long started) {}

    public static final class Pending {
        private final Plan plan;
        private final NavSegmentedSession session;
        private boolean done;
        private @Nullable RouteWalk walk;
        private String refusal = "";

        private Pending(Plan plan, NavSegmentedSession session) {
            this.plan = plan;
            this.session = session;
        }

        public boolean done() {
            return done;
        }

        public @Nullable RouteWalk walk() {
            return walk;
        }

        public String refusal() {
            return refusal;
        }
    }

    private RouteDetour() {}

    public static @Nullable Pending begin(SpiritEntity spirit, BlockCoord target, int radius, String why) {
        long now = spirit.level().getGameTime();
        boolean rescue = "dig_out".equals(why);
        if (!rescue && !THROTTLE.allow(spirit.getUUID(), now)) {
            LOGGER.info(
                    "[route] дух {} ({}): перестроение подавлено, прошлый расчёт меньше {} тиков назад",
                    spirit.displayName(),
                    why,
                    RouteThrottle.MIN_TICKS);
            return null;
        }
        BlockPos feet = spirit.blockPosition();
        BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        int blocks = RouteLevelView.blocks(spirit);
        RouteFailures.Key key = new RouteFailures.Key(spirit.getUUID(), from, target, radius, blocks);
        if (!rescue && FAILURES.recentlyFailed(key, now, FAIL_TTL_TICKS)) {
            LOGGER.info(
                    "[route] дух {} ({}): тот же безрезультатный расчёт к {} уже был меньше {} тиков назад — "
                            + "взят из кэша",
                    spirit.displayName(),
                    why,
                    target,
                    FAIL_TTL_TICKS);
            return null;
        }
        NavSettings settings = new NavSettings(2000, 2000, 200_000, 50, 3, 12, 2, 20, 2, false, false);
        LevelNavWorld live = new LevelNavWorld(spirit.level());
        NavCachedWorld cache = new NavCachedWorld();
        packLocal(live, cache, from);
        NavContext context = new NavContext(new NavCacheWorld(live, cache), settings, blocks);
        NavSegmentedSession session = new NavSegmentedSession(
                context,
                from,
                new NavGoalNear(target.x(), target.y(), target.z(), Math.max(1, radius)),
                MAX_SEGMENTS,
                MIN_CUTOFF,
                CUTOFF_FACTOR);
        return new Pending(new Plan(spirit, target, why, rescue, from, key, now, System.nanoTime()), session);
    }

    private static void packLocal(LevelNavWorld live, NavCachedWorld cache, BlockCoord from) {
        int minY = Math.max(live.minY(), from.y() - 24);
        int height = Math.min(live.maxY() - minY, 48);
        if (height > 0) {
            cache.put(NavCachedChunk.pack(from.x() >> 4, from.z() >> 4, minY, height, live.source()));
        }
    }

    public static void advance(SpiritEntity spirit, Pending pending, long budgetMillis) {
        if (pending.done) {
            return;
        }
        long watch = TickWatch.start();
        try {
            if ((System.nanoTime() - pending.plan.started()) / 1_000_000L > PLAN_TOTAL_MILLIS) {
                finish(pending, null, "поиск не уложился в " + PLAN_TOTAL_MILLIS + " мс");
                return;
            }
            NavSegmentedRoute segmented = pending.session.step(budgetMillis * 1_000_000L);
            if (segmented == null) {
                return;
            }
            Route route = NavRouteSteps.toRoute(segmented, new LevelNavWorld(spirit.level()), pending.plan.target());
            LOGGER.info(
                    "[route] дух {} ({}): из {} к {} — {}, сегментов {}, шагов {}, узлов {}, конец {}, {} мс",
                    spirit.displayName(),
                    pending.plan.why(),
                    pending.plan.from(),
                    pending.plan.target(),
                    route.status(),
                    segmented.segments(),
                    route.steps().size(),
                    route.nodes(),
                    route.end(),
                    segmented.millis());
            String refusal = refusal(spirit, pending, route);
            if (refusal.isEmpty()) {
                finish(pending, new RouteWalk(spirit, route, pending.plan.target()), "");
            } else {
                finish(pending, null, refusal);
            }
        } finally {
            TickWatch.done("RouteDetour", watch);
        }
    }

    private static String refusal(SpiritEntity spirit, Pending pending, Route route) {
        if (!route.usable()) {
            return "маршрут не найден: цель " + pending.plan.target();
        }
        if (waterTrap(spirit, new RouteLevelView(spirit), route, pending.plan.target())) {
            return "из воды нет выхода к поверхности (no_escape)";
        }
        return "";
    }

    private static void finish(Pending pending, @Nullable RouteWalk walk, String refusal) {
        pending.done = true;
        pending.walk = walk;
        pending.refusal = refusal;
        if (pending.plan.rescue()) {
            return;
        }
        if (walk != null || NavigationTickets.loading(pending.plan.spirit())) {
            FAILURES.forget(pending.plan.spirit().getUUID());
        } else {
            FAILURES.remember(pending.plan.key(), pending.plan.now());
        }
    }

    public static void forget(SpiritEntity spirit) {
        FAILURES.forget(spirit.getUUID());
    }

    public static @Nullable RouteWalk step(SpiritEntity spirit, Pending pending) {
        advance(spirit, pending, SLICE_MILLIS);
        return pending.done ? pending.walk : null;
    }

    public static int pendingBudget() {
        return (int) PLAN_TOTAL_MILLIS;
    }

    private static boolean waterTrap(SpiritEntity spirit, RouteLevelView world, Route route, BlockCoord target) {
        if (!spirit.isInWater() || route.status() != Route.Status.PARTIAL) {
            return false;
        }
        BlockCoord end = route.end();
        if (RouteCell.kind(world.cell(end.x(), end.y(), end.z())) != RouteCell.WATER) {
            return false;
        }
        LOGGER.warn(
                "[route] дух {}: из воды {} к {} построен только PARTIAL до воды {} — не иду: выхода к поверхности "
                        + "нет (no_escape)",
                spirit.displayName(),
                spirit.blockPosition().toShortString(),
                target,
                end);
        return true;
    }

    public static void removed(SpiritEntity spirit, RouteWalk walk) {
        if (!walk.removals().isEmpty()) {
            LOGGER.info(
                    "[route] дух {} проложил путь, сняв: {}",
                    spirit.displayName(),
                    walk.removals().summary());
        }
    }

    public static void failed(SpiritEntity spirit, RouteWalk walk) {
        LOGGER.warn(
                "[route] дух {} сошёл с маршрута в {}: {}",
                spirit.displayName(),
                spirit.blockPosition().toShortString(),
                walk.failure());
    }
}

package com.theminespirit.neoforge.movement;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.movement.GoalNear;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.route.Route;
import com.theminespirit.core.route.RoutePlanner;
import com.theminespirit.core.route.RouteStep;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

public final class RouteDetour {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long NANOS_PER_MICRO = 1_000L;

    private RouteDetour() {}

    public static Optional<RouteWalk> plan(SpiritEntity spirit, BlockCoord target, int radius, String why) {
        BlockPos feet = spirit.blockPosition();
        BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        long started = System.nanoTime();
        Route route = RoutePlanner.plan(
                new RouteLevelView(spirit),
                from,
                new GoalNear(target, Math.max(0, radius)),
                DataTables.INSTANCE.route().current(),
                RouteLevelView.blocks(spirit));
        long micros = (System.nanoTime() - started) / NANOS_PER_MICRO;
        LOGGER.info(
                "[route] дух {} ({}): из {} к {} — {}, цена {} тиков, сломать {}, поставить {}, шагов {}, конец {},"
                        + " узлов {}, чтений {}, {} мкс",
                spirit.displayName(),
                why,
                from,
                target,
                route.status(),
                String.format(Locale.ROOT, "%.1f", route.cost()),
                route.count(RouteStep.Action.BREAK),
                route.places(),
                route.steps().size(),
                route.end(),
                route.nodes(),
                route.reads(),
                micros);
        return route.usable() ? Optional.of(new RouteWalk(spirit, route)) : Optional.empty();
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

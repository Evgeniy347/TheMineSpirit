package com.theminespirit.neoforge.movement;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.movement.ApproachRoute;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.route.Route;
import com.theminespirit.core.route.RouteBreakAudit;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteStep;
import com.theminespirit.core.route.RouteThrottle;
import com.theminespirit.core.route.RouteWorld;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

public final class RouteDetour {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long NANOS_PER_MICRO = 1_000L;
    private static final int MAX_VETO_REPLANS = 3;
    private static final RouteThrottle THROTTLE = new RouteThrottle();

    private record PlanRequest(
            RouteWorld world, BlockCoord from, BlockCoord target, int radius, int blocks, LevelSafetyView safety) {}

    private RouteDetour() {}

    public static Optional<RouteWalk> plan(SpiritEntity spirit, BlockCoord target, int radius, String why) {
        long now = spirit.level().getGameTime();
        if (!THROTTLE.allow(spirit.getUUID(), now)) {
            LOGGER.info(
                    "[route] дух {} ({}): перестроение подавлено, прошлый расчёт меньше {} тиков назад",
                    spirit.displayName(),
                    why,
                    RouteThrottle.MIN_TICKS);
            return Optional.empty();
        }
        BlockPos feet = spirit.blockPosition();
        BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        RouteWorld world = new RouteLevelView(spirit);
        LevelSafetyView safety = new LevelSafetyView(spirit.level());
        int blocks = RouteLevelView.blocks(spirit);
        long started = System.nanoTime();
        PlanRequest request = new PlanRequest(world, from, target, radius, blocks, safety);
        Route route = plan(request);
        route = withoutVetoed(spirit, request, route);
        long micros = (System.nanoTime() - started) / NANOS_PER_MICRO;
        LOGGER.info(
                "[route] дух {} ({}): из {} к {} — {}, цена {} тиков, сломать {}, поставить {}, шагов {}, конец {},"
                        + " вода {}, узлов {}, чтений {}, {} мкс, BreakVeto отказов {}, первые {}",
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
                water(world, route),
                route.nodes(),
                route.reads(),
                micros,
                route.vetoCount(),
                route.vetoes());
        if (waterTrap(spirit, world, route, target)) {
            return Optional.empty();
        }
        return route.usable() ? Optional.of(new RouteWalk(spirit, route, target)) : Optional.empty();
    }

    private static boolean waterTrap(SpiritEntity spirit, RouteWorld world, Route route, BlockCoord target) {
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

    private static String water(RouteWorld world, Route route) {
        int count = 0;
        List<BlockCoord> first = new ArrayList<>();
        for (RouteStep step : route.steps()) {
            BlockCoord cell = step.cell();
            if ((step.action() == RouteStep.Action.WALK || step.action() == RouteStep.Action.SWIM)
                    && RouteCell.kind(world.cell(cell.x(), cell.y(), cell.z())) == RouteCell.WATER) {
                count++;
                if (first.size() < 5) {
                    first.add(cell);
                }
            }
        }
        return count == 0 ? "нет" : count + " " + first;
    }

    private static Route plan(PlanRequest request) {
        return ApproachRoute.plan(
                request.world(),
                request.from(),
                request.target(),
                Math.max(0, request.radius()),
                DataTables.INSTANCE.route().current(),
                request.blocks(),
                request.safety());
    }

    private static Route withoutVetoed(SpiritEntity spirit, PlanRequest request, Route route) {
        Set<BlockCoord> excluded = new HashSet<>();
        Route current = route;
        for (int attempt = 0; attempt < MAX_VETO_REPLANS; attempt++) {
            Optional<Route.Veto> veto = RouteBreakAudit.firstVeto(request.world(), request.from(), current);
            if (veto.isEmpty()) {
                return current;
            }
            Route.Veto found = veto.get();
            excluded.add(found.cell());
            LOGGER.warn(
                    "[route] дух {}: BreakVeto отказал BREAK в {}: {} — пересчёт без шага",
                    spirit.displayName(),
                    found.cell(),
                    found.reason());
            current = plan(new PlanRequest(
                    (x, y, z) -> excluded.contains(new BlockCoord(x, y, z))
                            ? RouteCell.BLOCKED
                            : request.world().cell(x, y, z),
                    request.from(),
                    request.target(),
                    request.radius(),
                    request.blocks(),
                    request.safety()));
        }
        return current;
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

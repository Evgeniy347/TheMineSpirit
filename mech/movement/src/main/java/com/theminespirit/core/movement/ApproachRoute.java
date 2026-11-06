package com.theminespirit.core.movement;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.Route;
import com.theminespirit.core.route.RouteBudget;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RoutePlanner;
import com.theminespirit.core.route.RouteRules;
import com.theminespirit.core.route.RouteWorld;
import java.util.Objects;
import java.util.Optional;

public final class ApproachRoute {
    public record Request(
            RouteWorld world,
            BlockCoord start,
            BlockCoord target,
            int radius,
            RouteRules rules,
            int blocks,
            SafetyView safety,
            RouteBudget budget) {}

    private ApproachRoute() {}

    public static Route plan(
            RouteWorld world,
            BlockCoord start,
            BlockCoord target,
            int radius,
            RouteRules rules,
            int blocks,
            SafetyView safety) {
        return plan(new Request(world, start, target, radius, rules, blocks, safety, RouteBudget.UNLIMITED));
    }

    public static Route plan(Request request) {
        Objects.requireNonNull(request, "Запрос маршрута не может быть null: без него нечего считать");
        RouteWorld world = request.world();
        BlockCoord start = request.start();
        BlockCoord target = request.target();
        RouteRules rules = request.rules();
        SafetyView safety = request.safety();
        RouteBudget budget = request.budget();
        Objects.requireNonNull(world, "Мир маршрута не может быть null: без клеток путь не построить");
        Objects.requireNonNull(start, "Старт не может быть null: без него неизвестно, откуда идти");
        Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, куда идти");
        Objects.requireNonNull(rules, "Правила маршрута не могут быть null: без цен ходов A* не работает");
        Objects.requireNonNull(safety, "Вид безопасности не может быть null: без него не выбрать стоянку");
        Objects.requireNonNull(budget, "Бюджет поиска не может быть null: без него предел работы неизвестен");
        Route direct = RoutePlanner.plan(
                world, start, new GoalNear(target, request.radius()), rules, request.blocks(), budget);
        if (!emptyRoute(direct)) {
            return direct;
        }
        return stand(world, start, target, rules, request.blocks(), safety, budget)
                .orElse(direct);
    }

    private static Optional<Route> stand(
            RouteWorld world,
            BlockCoord start,
            BlockCoord target,
            RouteRules rules,
            int blocks,
            SafetyView safety,
            RouteBudget budget) {
        SafetyView withoutStart = cell -> cell.equals(start) ? Cell.SOLID : safety.cellAt(cell);
        Optional<BlockCoord> chosen = StandChoice.choose(target, start, withoutStart);
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        Route route = RoutePlanner.plan(
                withoutBreaking(world), start, new GoalBlock(chosen.orElseThrow()), rules, blocks, budget);
        return route.usable() ? Optional.of(route) : Optional.empty();
    }

    private static RouteWorld withoutBreaking(RouteWorld world) {
        return (x, y, z) -> {
            int cell = world.cell(x, y, z);
            int kind = RouteCell.kind(cell);
            return kind == RouteCell.SOLID || kind == RouteCell.TALL ? RouteCell.BLOCKED : cell;
        };
    }

    private static boolean emptyRoute(Route route) {
        return route.status() == Route.Status.FOUND && route.steps().isEmpty();
    }
}

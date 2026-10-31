package com.theminespirit.core.route;

import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BreakVeto;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class RouteBreakAudit {
    private static final int PLACED_TICKS = 0;

    private final RouteWorld world;
    private final Map<BlockCoord, Integer> changed = new HashMap<>();
    private BlockCoord feet;

    private RouteBreakAudit(RouteWorld world, BlockCoord start) {
        this.world = world;
        this.feet = start;
    }

    public static Optional<Route.Veto> firstVeto(RouteWorld world, BlockCoord start, Route route) {
        RouteBreakAudit audit = new RouteBreakAudit(world, start);
        for (RouteStep step : route.steps()) {
            Optional<Route.Veto> veto = audit.apply(step);
            if (veto.isPresent()) {
                return veto;
            }
        }
        return Optional.empty();
    }

    private Optional<Route.Veto> apply(RouteStep step) {
        BlockCoord cell = step.cell();
        return switch (step.action()) {
            case BREAK -> {
                Optional<Route.Veto> veto = veto(cell);
                changed.put(cell, RouteCell.AIR);
                yield veto;
            }
            case PLACE -> {
                changed.put(cell, RouteCell.solid(PLACED_TICKS));
                yield Optional.empty();
            }
            case PILLAR -> {
                changed.put(cell, RouteCell.solid(PLACED_TICKS));
                feet = new BlockCoord(cell.x(), cell.y() + 1, cell.z());
                yield Optional.empty();
            }
            case WALK, SWIM -> {
                feet = cell;
                yield Optional.empty();
            }
        };
    }

    private Optional<Route.Veto> veto(BlockCoord target) {
        if (BreakVeto.checkBelowFeet(target, feet, this::safety) instanceof BreakSafety.Unsafe unsafe) {
            return Optional.of(new Route.Veto(target, unsafe.reason()));
        }
        return Optional.empty();
    }

    private Cell safety(BlockCoord coord) {
        Integer local = changed.get(coord);
        return RouteCell.safety(local != null ? local : world.cell(coord.x(), coord.y(), coord.z()));
    }
}

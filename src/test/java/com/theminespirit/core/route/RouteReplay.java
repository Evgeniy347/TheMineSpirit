package com.theminespirit.core.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class RouteReplay {
    private static final int REACH = 2;
    private static final int MAX_FALL = 3;
    private final RouteWorld world;
    private final Map<BlockCoord, Integer> changed = new HashMap<>();
    private BlockCoord feet;

    private RouteReplay(RouteWorld world, BlockCoord start) {
        this.world = world;
        this.feet = start;
    }

    static Optional<String> violation(RouteWorld world, BlockCoord start, Route route) {
        RouteReplay replay = new RouteReplay(world, start);
        for (RouteStep step : route.steps()) {
            Optional<String> broken = replay.apply(step);
            if (broken.isPresent()) {
                return Optional.of(step + ": " + broken.get());
            }
        }
        return replay.feet.equals(route.end())
                ? Optional.empty()
                : Optional.of("после шагов дух в " + replay.feet + ", а маршрут кончается в " + route.end());
    }

    private Optional<String> apply(RouteStep step) {
        BlockCoord cell = step.cell();
        int kind = RouteCell.kind(at(cell));
        return switch (step.action()) {
            case BREAK -> {
                if (!RouteCell.breakable(at(cell)) || far(cell)) {
                    yield Optional.of("ломка неразрушимого или дальнего блока " + kind);
                }
                if (lavaNear(cell)) {
                    yield Optional.of("ломка рядом с лавой");
                }
                changed.put(cell, RouteCell.AIR);
                yield Optional.empty();
            }
            case PLACE -> {
                if (kind != RouteCell.AIR || far(cell)) {
                    yield Optional.of("установка в занятую или дальнюю клетку " + kind);
                }
                changed.put(cell, GridWorld.DIRT);
                yield Optional.empty();
            }
            case PILLAR -> {
                if (!cell.equals(feet) || !RouteCell.open(at(up(cell, 2)))) {
                    yield Optional.of("столб не под ногами или некуда подняться");
                }
                changed.put(cell, GridWorld.DIRT);
                feet = up(cell, 1);
                yield Optional.empty();
            }
            case WALK -> walk(cell);
        };
    }

    private Optional<String> walk(BlockCoord to) {
        int horizontal = Math.abs(to.x() - feet.x()) + Math.abs(to.z() - feet.z());
        int rise = to.y() - feet.y();
        if (horizontal > 1 || rise > 1 || (horizontal == 0 && rise == 0)) {
            return Optional.of("шаг не к соседней клетке");
        }
        int fall = feet.y() - to.y();
        int body = at(to);
        int head = at(up(to, 1));
        boolean wet = RouteCell.kind(body) == RouteCell.WATER;
        if (!(RouteCell.open(body) || wet) || !(RouteCell.open(head) || RouteCell.kind(head) == RouteCell.WATER)) {
            return Optional.of("тело не помещается: " + RouteCell.kind(body) + "/" + RouteCell.kind(head));
        }
        boolean supported = RouteCell.supports(at(up(to, -1)));
        if (!supported && !wet && RouteCell.kind(body) != RouteCell.CLIMB) {
            return Optional.of("под ногами нет опоры");
        }
        if (fall > MAX_FALL && !wet) {
            return Optional.of("падение на " + fall + " блоков на сушу");
        }
        feet = to;
        return Optional.empty();
    }

    private boolean far(BlockCoord cell) {
        return Math.abs(cell.x() - feet.x()) > REACH
                || Math.abs(cell.z() - feet.z()) > REACH
                || cell.y() - feet.y() > REACH + 1
                || feet.y() - cell.y() > REACH;
    }

    private boolean lavaNear(BlockCoord cell) {
        int[][] sides = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] side : sides) {
            BlockCoord near = new BlockCoord(cell.x() + side[0], cell.y() + side[1], cell.z() + side[2]);
            if (RouteCell.kind(at(near)) == RouteCell.LAVA) {
                return true;
            }
        }
        return false;
    }

    private int at(BlockCoord cell) {
        Integer local = changed.get(cell);
        return local != null ? local : world.cell(cell.x(), cell.y(), cell.z());
    }

    private static BlockCoord up(BlockCoord cell, int dy) {
        return new BlockCoord(cell.x(), cell.y() + dy, cell.z());
    }
}

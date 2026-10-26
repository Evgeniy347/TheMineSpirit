package com.theminespirit.core.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class RouteMoves {
    static final int COUNT = 14;
    static final double IMPOSSIBLE = Double.POSITIVE_INFINITY;
    private static final int DIRECTIONS = 4;
    private static final int DOWN = 12;
    private static final int NO_LANDING = Integer.MIN_VALUE;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};
    private static final double WALK_OFF_SHARE = 0.8;

    private final RouteCells cells;
    private final RouteRules rules;
    private int toX;
    private int toY;
    private int toZ;
    private int placed;

    RouteMoves(RouteCells cells, RouteRules rules) {
        this.cells = cells;
        this.rules = rules;
    }

    double cost(int move, int x, int y, int z, int placesLeft, @Nullable List<RouteStep> out) {
        placed = 0;
        if (move < DOWN) {
            int nx = x + DX[move % DIRECTIONS];
            int nz = z + DZ[move % DIRECTIONS];
            return switch (move / DIRECTIONS) {
                case 0 -> traverse(new BlockCoord(x, y, z), nx, nz, placesLeft, out);
                case 1 -> ascend(new BlockCoord(x, y, z), nx, nz, placesLeft, out);
                default -> descend(nx, y, nz, out);
            };
        }
        return move == DOWN ? down(x, y, z, out) : up(x, y, z, placesLeft, out);
    }

    int toX() {
        return toX;
    }

    int toY() {
        return toY;
    }

    int toZ() {
        return toZ;
    }

    int placed() {
        return placed;
    }

    private double traverse(BlockCoord from, int nx, int nz, int placesLeft, @Nullable List<RouteStep> out) {
        int y = from.y();
        int feet = cells.at(nx, y, nz);
        int below = cells.at(nx, y - 1, nz);
        boolean wet = RouteCell.kind(feet) == RouteCell.WATER;
        boolean bridge = !RouteCell.supports(below) && !wet && RouteCell.kind(feet) != RouteCell.CLIMB;
        if (bridge && (placesLeft < 1 || RouteCell.kind(below) != RouteCell.AIR || !standing(from.x(), y, from.z()))) {
            return IMPOSSIBLE;
        }
        double cost = clear(nx, y + 1, nz, out) + clear(nx, y, nz, out);
        if (bridge) {
            cost += place(nx, y - 1, nz, out);
        }
        return arrive(nx, y, nz, cost + (wet ? rules.waterTicks() : rules.walkTicks()), out);
    }

    private double ascend(BlockCoord from, int nx, int nz, int placesLeft, @Nullable List<RouteStep> out) {
        int x = from.x();
        int y = from.y();
        int z = from.z();
        if (!standing(x, y, z) && RouteCell.kind(cells.at(x, y, z)) != RouteCell.WATER) {
            return IMPOSSIBLE;
        }
        int step = cells.at(nx, y, nz);
        boolean fill = !RouteCell.supports(step);
        if (fill
                && (placesLeft < 1
                        || RouteCell.kind(step) != RouteCell.AIR
                        || !RouteCell.supports(cells.at(nx, y - 1, nz)))) {
            return IMPOSSIBLE;
        }
        double cost = clear(x, y + 2, z, out) + clear(nx, y + 2, nz, out) + clear(nx, y + 1, nz, out);
        if (fill) {
            cost += place(nx, y, nz, out);
        }
        return arrive(nx, y + 1, nz, cost + rules.walkTicks() + rules.jumpPenalty(), out);
    }

    private double descend(int nx, int y, int nz, @Nullable List<RouteStep> out) {
        int feet = cells.at(nx, y, nz);
        if (RouteCell.supports(cells.at(nx, y - 1, nz)) || RouteCell.kind(feet) == RouteCell.WATER) {
            return IMPOSSIBLE;
        }
        int land = landing(nx, y, nz);
        if (land == NO_LANDING) {
            return IMPOSSIBLE;
        }
        double cost = clear(nx, y + 1, nz, out) + clear(nx, y, nz, out);
        double fall = (y - land) * rules.fallTicks() + rules.walkTicks() * WALK_OFF_SHARE;
        return arrive(nx, land, nz, cost + fall + wetLanding(nx, land, nz), out);
    }

    private double down(int x, int y, int z, @Nullable List<RouteStep> out) {
        if (!standing(x, y, z) || !RouteCell.breakable(cells.at(x, y - 1, z))) {
            return IMPOSSIBLE;
        }
        int land = landing(x, y - 1, z);
        if (land == NO_LANDING) {
            return IMPOSSIBLE;
        }
        double cost = clear(x, y - 1, z, out);
        return arrive(x, land, z, cost + (y - land) * rules.fallTicks() + wetLanding(x, land, z), out);
    }

    private double up(int x, int y, int z, int placesLeft, @Nullable List<RouteStep> out) {
        int feet = RouteCell.kind(cells.at(x, y, z));
        int above = cells.at(x, y + 1, z);
        if (feet == RouteCell.CLIMB || feet == RouteCell.WATER) {
            if (!RouteCell.open(above) && RouteCell.kind(above) != RouteCell.WATER) {
                return IMPOSSIBLE;
            }
            double swim = feet == RouteCell.CLIMB ? rules.climbTicks() : rules.waterTicks();
            return arrive(x, y + 1, z, clear(x, y + 2, z, out) + swim, out);
        }
        if (placesLeft < 1 || !standing(x, y, z) || !RouteCell.open(above)) {
            return IMPOSSIBLE;
        }
        double cost = clear(x, y + 2, z, out);
        if (cost == IMPOSSIBLE || nearLava(x, y + 1, z)) {
            return IMPOSSIBLE;
        }
        placed++;
        if (out != null) {
            out.add(new RouteStep(RouteStep.Action.PILLAR, new BlockCoord(x, y, z)));
        }
        toX = x;
        toY = y + 1;
        toZ = z;
        return cost + rules.walkTicks() + rules.jumpPenalty() + rules.placeTicks();
    }

    private int landing(int x, int y, int z) {
        for (int depth = 1; depth <= rules.maxWaterFall(); depth++) {
            int cell = cells.at(x, y - depth, z);
            int kind = RouteCell.kind(cell);
            if (RouteCell.supports(cell)) {
                return depth - 1 <= rules.maxFall() ? y - depth + 1 : NO_LANDING;
            }
            if (kind == RouteCell.WATER || kind == RouteCell.CLIMB) {
                return y - depth;
            }
            if (kind != RouteCell.AIR) {
                return NO_LANDING;
            }
        }
        return NO_LANDING;
    }

    private double wetLanding(int x, int y, int z) {
        return RouteCell.kind(cells.at(x, y, z)) == RouteCell.WATER ? rules.waterTicks() : 0.0;
    }

    private boolean standing(int x, int y, int z) {
        return RouteCell.supports(cells.at(x, y - 1, z)) || RouteCell.kind(cells.at(x, y, z)) == RouteCell.CLIMB;
    }

    private double clear(int x, int y, int z, @Nullable List<RouteStep> out) {
        int cell = cells.at(x, y, z);
        if (RouteCell.open(cell) || RouteCell.kind(cell) == RouteCell.WATER) {
            return 0.0;
        }
        if (!RouteCell.breakable(cell) || floods(x, y, z)) {
            return IMPOSSIBLE;
        }
        if (out != null) {
            out.add(new RouteStep(RouteStep.Action.BREAK, new BlockCoord(x, y, z)));
        }
        return RouteCell.breakTicks(cell) + rules.breakPenalty();
    }

    private boolean floods(int x, int y, int z) {
        int above = RouteCell.kind(cells.at(x, y + 1, z));
        if (above == RouteCell.WATER || above == RouteCell.LAVA) {
            return true;
        }
        for (int side = 0; side < DIRECTIONS; side++) {
            if (RouteCell.kind(cells.at(x + DX[side], y, z + DZ[side])) == RouteCell.LAVA) {
                return true;
            }
        }
        return false;
    }

    private boolean nearLava(int x, int y, int z) {
        for (int side = 0; side < DIRECTIONS; side++) {
            int sideX = x + DX[side];
            int sideZ = z + DZ[side];
            if (RouteCell.kind(cells.at(sideX, y, sideZ)) == RouteCell.LAVA
                    || RouteCell.kind(cells.at(sideX, y - 1, sideZ)) == RouteCell.LAVA) {
                return true;
            }
        }
        return false;
    }

    private double place(int x, int y, int z, @Nullable List<RouteStep> out) {
        placed++;
        if (out != null) {
            out.add(new RouteStep(RouteStep.Action.PLACE, new BlockCoord(x, y, z)));
        }
        return rules.placeTicks();
    }

    private double arrive(int x, int y, int z, double cost, @Nullable List<RouteStep> out) {
        if (cost == IMPOSSIBLE || nearLava(x, y, z)) {
            return IMPOSSIBLE;
        }
        if (out != null) {
            out.add(new RouteStep(RouteStep.Action.WALK, new BlockCoord(x, y, z)));
        }
        toX = x;
        toY = y;
        toZ = z;
        return cost;
    }
}

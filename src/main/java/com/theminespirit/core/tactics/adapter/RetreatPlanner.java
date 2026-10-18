package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class RetreatPlanner {
    public static final double AWAY_DISTANCE = 12.0;
    public static final double KITE_DISTANCE = 10.0;
    public static final double HOME_ARRIVAL = 2.0;
    public static final double SPOT_ARRIVAL = 0.5;
    public static final double WATER_ARRIVAL = 1.2;
    public static final double RUN_ARRIVAL = 1.5;
    public static final int PILLAR_HEIGHT = 3;
    public static final int WALL_HALF_WIDTH = 1;
    public static final int WALL_HEIGHT = 2;

    public record Input(Point self, BlockCoord feet, List<EnemyFact> enemies, EnvReading env, Optional<Point> home,
            int blocks, boolean waterBucket) {
        public Input {
            if (self == null || feet == null || enemies == null || env == null || home == null) {
                throw new IllegalArgumentException("RetreatPlanner.Input: self/feet/enemies/env/home = null; "
                        + "план отхода строится по снятым фактам, отсутствие дома — Optional.empty()");
            }
            if (blocks < 0) {
                throw new IllegalArgumentException("RetreatPlanner.Input.blocks = " + blocks
                        + ": число блоков в инвентаре не может быть отрицательным");
            }
            enemies = List.copyOf(enemies);
        }
    }

    private RetreatPlanner() {
    }

    public static Optional<RetreatPlan> plan(RetreatMethod method, Input input) {
        if (method == null || input == null) {
            throw new IllegalArgumentException("RetreatPlanner.plan: method/input = null; план строится по способу "
                    + "отхода из решения ядра и снятым фактам");
        }
        return switch (method) {
            case HOME -> input.home().map(home -> new RetreatPlan.Walk(home, HOME_ARRIVAL, false));
            case COVER -> input.env().cover().map(cell -> new RetreatPlan.Walk(center(cell), SPOT_ARRIVAL, false));
            case WATER -> water(input);
            case KITE -> Optional.of(new RetreatPlan.Walk(awayPoint(input, KITE_DISTANCE), RUN_ARRIVAL, true));
            case AWAY -> Optional.of(new RetreatPlan.Walk(awayPoint(input, AWAY_DISTANCE), RUN_ARRIVAL, false));
            case BARRICADE -> barricade(input);
            case PILLAR -> input.blocks() < 1 ? Optional.empty()
                    : Optional.of(new RetreatPlan.Tower(Math.min(PILLAR_HEIGHT, input.blocks())));
            case NONE -> Optional.empty();
        };
    }

    private static Optional<RetreatPlan> water(Input input) {
        Optional<RetreatPlan> found = input.env().water()
                .map(cell -> new RetreatPlan.Walk(center(cell), WATER_ARRIVAL, false));
        if (found.isPresent()) {
            return found;
        }
        if (input.waterBucket()) {
            return Optional.of(new RetreatPlan.Pour());
        }
        return input.home().map(home -> new RetreatPlan.Walk(home, HOME_ARRIVAL, false));
    }

    static Point awayPoint(Input input, double distance) {
        Point self = input.self();
        Optional<EnemyFact> nearest = nearest(input);
        double dx = nearest.map(enemy -> self.x() - enemy.pos().x()).orElse(1.0);
        double dz = nearest.map(enemy -> self.z() - enemy.pos().z()).orElse(0.0);
        double length = Math.hypot(dx, dz);
        if (length < 1e-6) {
            dx = 1.0;
            dz = 0.0;
            length = 1.0;
        }
        return new Point(self.x() + dx / length * distance, self.y(), self.z() + dz / length * distance);
    }

    private static Optional<RetreatPlan> barricade(Input input) {
        if (input.blocks() < 1) {
            return Optional.empty();
        }
        Optional<EnemyFact> nearest = nearest(input);
        double dx = nearest.map(enemy -> enemy.pos().x() - input.self().x()).orElse(1.0);
        double dz = nearest.map(enemy -> enemy.pos().z() - input.self().z()).orElse(0.0);
        int facingX = Math.abs(dx) >= Math.abs(dz) ? (int) Math.signum(dx) : 0;
        int facingZ = facingX == 0 ? (int) Math.signum(dz) : 0;
        int sideX = -facingZ;
        int sideZ = facingX;
        List<BlockCoord> cells = new ArrayList<>();
        int[] order = {0, -1, 1};
        for (int side : order) {
            for (int height = 0; height < WALL_HEIGHT; height++) {
                cells.add(new BlockCoord(input.feet().x() + facingX + sideX * side, input.feet().y() + height,
                        input.feet().z() + facingZ + sideZ * side));
            }
        }
        return Optional.of(new RetreatPlan.Wall(cells.subList(0, Math.min(cells.size(), input.blocks()))));
    }

    private static Optional<EnemyFact> nearest(Input input) {
        return input.enemies().stream().min(Comparator.comparingDouble((EnemyFact enemy) -> enemy.pos().distanceTo(input.self()))
                .thenComparing(EnemyFact::id));
    }

    private static Point center(BlockCoord cell) {
        return new Point(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
    }
}

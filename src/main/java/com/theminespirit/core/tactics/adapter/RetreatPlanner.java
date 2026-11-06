package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class RetreatPlanner {
    public static final double AWAY_DISTANCE = 12.0;
    public static final double KITE_DISTANCE = 10.0;
    public static final double HOME_ARRIVAL = RetreatSpacing.HOME_ARRIVAL;
    public static final double SPOT_ARRIVAL = 0.5;
    public static final double WATER_ARRIVAL = 1.2;
    public static final double RUN_ARRIVAL = 1.5;
    public static final double RALLY_ARRIVAL = 1.5;

    public record Input(
            Point self,
            BlockCoord feet,
            List<EnemyFact> enemies,
            EnvReading env,
            Optional<Point> home,
            int blocks,
            Optional<Point> rally,
            boolean waterBucket) {
        public Input {
            if (self == null || feet == null || enemies == null || env == null || home == null || rally == null) {
                throw new IllegalArgumentException("RetreatPlanner.Input: self/feet/enemies/env/home/rally = null; "
                        + "план отхода строится по снятым фактам, отсутствие дома или точки сбора — Optional.empty()");
            }
            if (blocks < 0) {
                throw new IllegalArgumentException("RetreatPlanner.Input.blocks = " + blocks
                        + ": число блоков в инвентаре не может быть отрицательным");
            }
            enemies = List.copyOf(enemies);
        }
    }

    private RetreatPlanner() {}

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
            case RALLY -> input.rally().map(point -> new RetreatPlan.Walk(point, RALLY_ARRIVAL, false));
            case AWAY -> Optional.of(new RetreatPlan.Walk(awayPoint(input, AWAY_DISTANCE), RUN_ARRIVAL, false));
            case NONE -> Optional.empty();
        };
    }

    private static Optional<RetreatPlan> water(Input input) {
        Optional<RetreatPlan> found =
                input.env().water().map(cell -> new RetreatPlan.Walk(center(cell), WATER_ARRIVAL, false));
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

    private static Optional<EnemyFact> nearest(Input input) {
        return input.enemies().stream()
                .min(Comparator.comparingDouble((EnemyFact enemy) -> enemy.pos().distanceTo(input.self()))
                        .thenComparing(EnemyFact::id));
    }

    private static Point center(BlockCoord cell) {
        return new Point(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
    }
}

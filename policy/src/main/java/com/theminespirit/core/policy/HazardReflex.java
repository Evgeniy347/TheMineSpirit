package com.theminespirit.core.policy;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.Set;

public final class HazardReflex {
    private static final int[][] SIDE_STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final Set<Cell> BURNING = Set.of(Cell.LAVA, Cell.FIRE);

    private HazardReflex() {}

    public static PolicyVerdict evaluate(
            LocalSurroundings surroundings, PolicyExemptions exemptions, SafetyView world) {
        BodyFlags flags = surroundings.flags();
        if (flags.inLava() || burningInColumn(surroundings)) {
            return escape(surroundings, world, "дух в лаве или огне");
        }
        if (flags.onFire() && !flags.inWater() && surroundings.nearestWater().isPresent()) {
            return new PolicyVerdict.Reflex(
                    PolicyId.LAVA_FIRE,
                    new ReflexAction.Flee(surroundings.nearestWater().get()),
                    "дух горит: цель — вода");
        }
        if (burningBeside(surroundings)) {
            return escape(surroundings, world, "лава или огонь вплотную на уровне ног или пола");
        }
        if (hazardTouching(surroundings)) {
            return escape(surroundings, world, "опасный блок (кактус, магма, ягоды, паутина, снег) касается духа");
        }
        if (flags.inWater() && !exemptions.has(PolicyId.WATER)) {
            return exitWater(surroundings, world);
        }
        return new PolicyVerdict.Clear();
    }

    private static boolean burningInColumn(LocalSurroundings surroundings) {
        for (int dy = -1; dy <= 1; dy++) {
            if (BURNING.contains(surroundings.cellAt(0, dy, 0))) {
                return true;
            }
        }
        return false;
    }

    private static boolean burningBeside(LocalSurroundings surroundings) {
        for (int[] step : SIDE_STEPS) {
            if (BURNING.contains(surroundings.cellAt(step[0], 0, step[1]))
                    || BURNING.contains(surroundings.cellAt(step[0], -1, step[1]))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hazardTouching(LocalSurroundings surroundings) {
        if (surroundings.cellAt(0, 0, 0) == Cell.HAZARD) {
            return true;
        }
        if (!surroundings.flags().hurtRecently()) {
            return false;
        }
        if (surroundings.cellAt(0, -1, 0) == Cell.HAZARD) {
            return true;
        }
        for (int[] step : SIDE_STEPS) {
            if (surroundings.cellAt(step[0], 0, step[1]) == Cell.HAZARD) {
                return true;
            }
        }
        return false;
    }

    private static PolicyVerdict escape(LocalSurroundings surroundings, SafetyView world, String reason) {
        Optional<BlockCoord> cell = nearestStandable(surroundings, false, world);
        if (cell.isPresent()) {
            return new PolicyVerdict.Reflex(PolicyId.LAVA_FIRE, new ReflexAction.Flee(cell.get()), reason);
        }
        return new PolicyVerdict.Reflex(
                PolicyId.LAVA_FIRE, new ReflexAction.StopMoving(), reason + ", безопасной клетки рядом нет");
    }

    private static PolicyVerdict exitWater(LocalSurroundings surroundings, SafetyView world) {
        Optional<BlockCoord> cell = nearestStandable(surroundings, true, world);
        if (cell.isPresent()) {
            return new PolicyVerdict.Reflex(PolicyId.WATER, new ReflexAction.ExitWater(cell.get()), "дух в воде");
        }
        // lib: берега рядом нет — дух в ловушке, а не в рефлексе: держать его на поверхности вечно нельзя,
        // lib: иначе спасение (dig_out) не выполнится (10.11: шестеро стояли в -99,52,-417 больше трёх часов).
        return new PolicyVerdict.Clear();
    }

    private static Optional<BlockCoord> nearestStandable(
            LocalSurroundings surroundings, boolean tolerateWater, SafetyView world) {
        return NearestExit.find(surroundings, new ExitView(surroundings, world, tolerateWater), world);
    }

    private record ExitView(LocalSurroundings surroundings, SafetyView world, boolean tolerateWater)
            implements SafetyView {
        @Override
        public Cell cellAt(BlockCoord coord) {
            BlockCoord feet = surroundings.feet();
            Cell cell = world.cellAt(coord);
            if (coord.equals(feet)) {
                return Cell.PASSABLE;
            }
            if (coord.x() == feet.x() && coord.z() == feet.z() && coord.y() == feet.y() - 1 && cell != Cell.SOLID) {
                return Cell.SOLID;
            }
            return tolerateWater && cell == Cell.WATER ? Cell.PASSABLE : cell;
        }
    }
}

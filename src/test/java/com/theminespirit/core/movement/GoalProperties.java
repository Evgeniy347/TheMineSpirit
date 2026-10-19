package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class GoalProperties {
    private static final double EPS = 1e-9;

    @Property
    void composite_Heuristic_EqualsMinimumOfNested(
            @ForAll @IntRange(min = -500, max = 500) int ax,
            @ForAll @IntRange(min = -500, max = 500) int bx,
            @ForAll @IntRange(min = -64, max = 319) int y,
            @ForAll @IntRange(min = -500, max = 500) int fx) {
        BlockCoord first = new BlockCoord(ax, 0, 0);
        BlockCoord second = new BlockCoord(bx, 0, 0);
        BlockCoord from = new BlockCoord(fx, y, 0);
        GoalComposite composite = new GoalComposite(List.of(new GoalBlock(first), new GoalBlock(second)));

        assertEquals(
                Math.min(new GoalBlock(first).heuristic(from), new GoalBlock(second).heuristic(from)),
                composite.heuristic(from),
                EPS,
                "Эвристика composite обязана равняться минимуму эвристик вложенных: иначе goto_block "
                        + "выберет не ближайший найденный блок, а случайный");
    }

    @Property
    void heuristic_AnyGoal_NeverExceedsOctileDistance(
            @ForAll @IntRange(min = -500, max = 500) int tx,
            @ForAll @IntRange(min = -500, max = 500) int tz,
            @ForAll @IntRange(min = -64, max = 319) int ty,
            @ForAll @IntRange(min = -500, max = 500) int fx,
            @ForAll @IntRange(min = -500, max = 500) int fz,
            @ForAll @IntRange(min = -64, max = 319) int fy,
            @ForAll @IntRange(min = 0, max = 32) int radius) {
        BlockCoord target = new BlockCoord(tx, ty, tz);
        BlockCoord from = new BlockCoord(fx, fy, fz);
        double octile = Goal.octile(from, target);
        List<Goal> goals = List.of(
                new GoalBlock(target),
                new GoalXZ(tx, tz),
                new GoalNear(target, radius),
                new GoalComposite(List.of(new GoalBlock(target), new GoalXZ(tx, tz))));

        for (Goal goal : goals) {
            assertTrue(
                    goal.heuristic(from) <= octile + EPS,
                    "Эвристика " + goal + " обязана не превышать истинную октиальную дистанцию " + octile
                            + ": завышенная эвристика недопустима для A* и ломает порядок целей");
        }
    }
}

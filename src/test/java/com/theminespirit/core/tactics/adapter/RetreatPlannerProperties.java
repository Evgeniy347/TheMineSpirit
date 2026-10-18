package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnvFacts;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class RetreatPlannerProperties {
    private static final Point SELF = new Point(0.5, 1, 0.5);
    private static final BlockCoord FEET = new BlockCoord(0, 1, 0);
    private static final EnvReading FULL_ENV = new EnvReading(EnvFacts.FAR_DISTANCE, 0,
            Optional.of(new BlockCoord(-4, 0, 3)), 5.0, Optional.of(new BlockCoord(-2, 1, -3)), 3.6, 10, false);

    @Provide
    Arbitrary<EnemyFact> enemy() {
        return Combinators.combine(Arbitraries.integers().between(-30, 30), Arbitraries.integers().between(-30, 30))
                .as((x, z) -> new EnemyFact("minecraft:zombie", "z", new Point(x + 0.5, 1, z + 0.5), 1, 0, true, true));
    }

    @Property(tries = 200)
    void plan_EveryRealMethod_GivesExactlyOneKindOfPlanWhenTargetsExist(@ForAll("enemy") EnemyFact enemy,
            @ForAll @IntRange(min = 8, max = 64) int blocks) {
        RetreatPlanner.Input input = new RetreatPlanner.Input(SELF, FEET, List.of(enemy), FULL_ENV,
                Optional.of(new Point(-8.5, 1, 0.5)), blocks, Optional.of(new Point(-4.5, 1, 0.5)), false);

        for (RetreatMethod method : RetreatMethod.values()) {
            Optional<RetreatPlan> plan = RetreatPlanner.plan(method, input);
            if (method == RetreatMethod.NONE) {
                assertTrue(plan.isEmpty(), "NONE означает «не отходить»: плана нет");
                continue;
            }
            assertTrue(plan.isPresent(), "Способ " + method + " при наличии цели, дома и блоков обязан дать план");
            boolean walk = plan.get() instanceof RetreatPlan.Walk;
            boolean wall = plan.get() instanceof RetreatPlan.Wall;
            boolean tower = plan.get() instanceof RetreatPlan.Tower;
            boolean pour = plan.get() instanceof RetreatPlan.Pour;
            int kinds = (walk ? 1 : 0) + (wall ? 1 : 0) + (tower ? 1 : 0) + (pour ? 1 : 0);
            assertTrue(kinds == 1, "Способ " + method + " даёт ровно один вид плана, получили " + plan.get());
        }
    }

    @Property(tries = 200)
    void plan_Barricade_NeverPlansMoreCellsThanBlocksInInventory(@ForAll("enemy") EnemyFact enemy,
            @ForAll @IntRange(min = 0, max = 12) int blocks) {
        RetreatPlanner.Input input = new RetreatPlanner.Input(SELF, FEET, List.of(enemy), EnvReading.NOTHING,
                Optional.empty(), blocks, Optional.empty(), false);

        Optional<RetreatPlan> plan = RetreatPlanner.plan(RetreatMethod.BARRICADE, input);

        int cells = plan.map(p -> ((RetreatPlan.Wall) p).cells().size()).orElse(0);
        assertTrue(cells <= blocks, "Баррикада планирует " + cells + " клеток при " + blocks
                + " блоках в инвентаре: блоки не создаются из воздуха, лишние клетки значат ломку чужого запаса");
    }

    @Property(tries = 200)
    void plan_Away_TargetIsFartherFromNearestEnemyThanSelf(@ForAll("enemy") EnemyFact enemy) {
        RetreatPlanner.Input input = new RetreatPlanner.Input(SELF, FEET, List.of(enemy), EnvReading.NOTHING,
                Optional.empty(), 0, Optional.empty(), false);

        RetreatPlan.Walk walk = (RetreatPlan.Walk) RetreatPlanner.plan(RetreatMethod.AWAY, input).orElseThrow();

        assertTrue(walk.target().distanceTo(enemy.pos()) > SELF.distanceTo(enemy.pos()),
                "Бег прочь обязан увеличивать расстояние до врага: цель " + walk.target() + " не дальше духа");
    }
}

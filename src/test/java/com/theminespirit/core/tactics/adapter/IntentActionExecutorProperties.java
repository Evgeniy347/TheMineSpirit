package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalAction;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class IntentActionExecutorProperties {
    @Provide
    Arbitrary<TacticalOutcome> outcomes() {
        return Combinators.combine(Arbitraries.of(TacticalOption.values()), Arbitraries.of(TacticalAction.values()),
                Arbitraries.of(RetreatMethod.values()))
                .as((option, action, method) -> AdapterFixtures.outcome(option, action, method));
    }

    @Provide
    Arbitrary<List<EnemyFact>> enemies() {
        Arbitrary<EnemyFact> enemy = Combinators.combine(
                Arbitraries.of("minecraft:zombie", "minecraft:skeleton", "minecraft:creeper"),
                Arbitraries.integers().between(0, 5), Arbitraries.integers().between(0, 20),
                Arbitraries.of(true, false))
                .as((type, id, distance, visible) -> new EnemyFact(type, "id" + id, new Point(distance, 0, 0), 1, 0,
                        visible, true));
        return enemy.list().ofMinSize(0).ofMaxSize(10);
    }

    @Property(tries = 200)
    void execute_AnyOutcome_IsDeterministicAndInFamily(@ForAll("outcomes") TacticalOutcome outcome,
            @ForAll("enemies") List<EnemyFact> enemies) {
        IntentActionExecutor executor = new IntentActionExecutor();
        SituationContext context = AdapterFixtures.context(enemies);

        ExecutionResult first = executor.execute(outcome, context);
        ExecutionResult second = executor.execute(outcome, context);

        assertEquals(first, second,
                "Повтор одного решения даёт тот же результат: исполнение обязано быть детерминированным");
        assertTrue(family(outcome.decision()).contains(first.getClass()),
                "Решению " + outcome.decision() + " соответствует только его семейство интентов, получили "
                        + first.getClass().getSimpleName());
    }

    private static Set<Class<?>> family(TacticalOption option) {
        return switch (option) {
            case FIGHT -> Set.of(ExecutionResult.Attacking.class, ExecutionResult.Shooting.class,
                    ExecutionResult.Idle.class);
            case RETREAT -> Set.of(ExecutionResult.Moving.class, ExecutionResult.Idle.class);
            case IGNORE -> Set.of(ExecutionResult.Idle.class);
        };
    }
}

package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalAction;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class TargetDesignationProperties {
    @Provide
    Arbitrary<List<EnemyFact>> enemies() {
        return Arbitraries.integers().between(1, 40).list().ofMinSize(1).ofMaxSize(6)
                .map(distances -> {
                    List<EnemyFact> result = new ArrayList<>();
                    for (int i = 0; i < distances.size(); i++) {
                        result.add(AdapterFixtures.enemy("minecraft:zombie", "e" + i, distances.get(i), i, i % 2 == 0));
                    }
                    return result;
                });
    }

    private static TacticalOutcome outcome(TacticalOption option, TacticalAction action, CombatMode mode) {
        TacticalOutcome base = AdapterFixtures.outcome(option, action, RetreatMethod.NONE);
        return new TacticalOutcome(base.decision(), base.action(), base.retreatMethod(), base.callHelp(), base.state(),
                mode, Optional.empty(), base.trace(), base.memory());
    }

    @Property(tries = 200)
    void pick_PassiveMode_NeverDesignatesTarget(@ForAll("enemies") List<EnemyFact> enemies,
            @ForAll TacticalOption option, @ForAll TacticalAction action) {
        Optional<EnemyFact> target = TargetDesignation.pick(outcome(option, action, CombatMode.PASSIVE),
                AdapterFixtures.context(enemies));

        assertTrue(target.isEmpty(), "PASSIVE не выставляет цель никогда, даже при решении " + option + "/" + action
                + ": пассивный дух не начинает драку; удар в углу идёт без выбора цели");
    }

    @Property(tries = 200)
    void pick_AggressiveFight_AlwaysDesignatesTargetFromEnemies(@ForAll("enemies") List<EnemyFact> enemies,
            @ForAll("fightActions") TacticalAction action) {
        Optional<EnemyFact> target = TargetDesignation.pick(outcome(TacticalOption.FIGHT, action, CombatMode.AGGRESSIVE),
                AdapterFixtures.context(enemies));

        assertTrue(target.isPresent() && enemies.contains(target.get()),
                "AGGRESSIVE при решении FIGHT всегда выставляет цель из снятых врагов, иначе дух стоит среди врагов");
    }

    @Provide
    Arbitrary<TacticalAction> fightActions() {
        return Arbitraries.of(TacticalAction.ENGAGE, TacticalAction.SHOOT, TacticalAction.DEFEND_AREA,
                TacticalAction.PROTECT);
    }
}

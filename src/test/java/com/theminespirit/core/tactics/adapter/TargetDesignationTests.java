package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalAction;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TargetDesignationTests {
    private static final EnemyFact NEAR = AdapterFixtures.enemy("minecraft:zombie", "near", 4, 0);
    private static final EnemyFact FAR = AdapterFixtures.enemy("minecraft:zombie", "far", 12, 0);
    private static final EnemyFact HIDDEN = AdapterFixtures.enemy("minecraft:zombie", "hidden", 2, 0, false);

    record Row(String name, CombatMode mode, TacticalOption option, TacticalAction action, List<EnemyFact> enemies,
            Optional<String> expectedId) {
    }

    static Stream<Row> rows() {
        return Stream.of(
                new Row("AGGRESSIVE и FIGHT: цель — ближний враг", CombatMode.AGGRESSIVE, TacticalOption.FIGHT,
                        TacticalAction.ENGAGE, List.of(FAR, NEAR), Optional.of("near")),
                new Row("AGGRESSIVE и FIGHT с луком: цель та же, стрелять в неё", CombatMode.AGGRESSIVE,
                        TacticalOption.FIGHT, TacticalAction.SHOOT, List.of(FAR, NEAR), Optional.of("near")),
                new Row("PASSIVE и FIGHT (загнан в угол): цель не выставляется", CombatMode.PASSIVE,
                        TacticalOption.FIGHT, TacticalAction.ENGAGE, List.of(NEAR), Optional.empty()),
                new Row("DEFENSIVE и FIGHT: цель — ближний", CombatMode.DEFENSIVE, TacticalOption.FIGHT,
                        TacticalAction.ENGAGE, List.of(NEAR), Optional.of("near")),
                new Row("PROTECT и FIGHT: цель — ближний", CombatMode.PROTECT, TacticalOption.FIGHT,
                        TacticalAction.PROTECT, List.of(NEAR), Optional.of("near")),
                new Row("GUARD_AREA и FIGHT: цель — ближний", CombatMode.GUARD_AREA, TacticalOption.FIGHT,
                        TacticalAction.DEFEND_AREA, List.of(NEAR), Optional.of("near")),
                new Row("RETREAT: цели нет", CombatMode.AGGRESSIVE, TacticalOption.RETREAT, TacticalAction.RETREAT,
                        List.of(NEAR), Optional.empty()),
                new Row("IGNORE: цели нет", CombatMode.AGGRESSIVE, TacticalOption.IGNORE, TacticalAction.CONTINUE,
                        List.of(NEAR), Optional.empty()),
                new Row("FIGHT без врагов: цели нет", CombatMode.AGGRESSIVE, TacticalOption.FIGHT,
                        TacticalAction.ENGAGE, List.of(), Optional.empty()),
                new Row("видимый дальний важнее невидимого ближнего", CombatMode.AGGRESSIVE, TacticalOption.FIGHT,
                        TacticalAction.ENGAGE, List.of(HIDDEN, FAR), Optional.of("far")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void pick_Outcome_GivesExpectedTarget(Row row) {
        TacticalOutcome base = AdapterFixtures.outcome(row.option(), row.action(), RetreatMethod.NONE);
        TacticalOutcome outcome = new TacticalOutcome(base.decision(), base.action(), base.retreatMethod(),
                base.callHelp(), base.state(), row.mode(), Optional.empty(), base.trace(), base.memory());

        Optional<String> picked = TargetDesignation.pick(outcome, AdapterFixtures.context(row.enemies()))
                .map(EnemyFact::id);

        assertEquals(row.expectedId(), picked, "Цель духа («" + row.name() + "»): только FIGHT выставляет цель, "
                + "PASSIVE не выставляет никогда, видимый враг важнее ближнего невидимого");
    }

    @Test
    void pick_NullOutcome_Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TargetDesignation.pick(null, AdapterFixtures.context(List.of())),
                "Решение обязательно: цель выбирают по нему, а не угадывают");
    }
}

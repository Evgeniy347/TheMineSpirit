package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalAction;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IntentActionExecutorTests {
    private static final String ZOMBIE = "minecraft:zombie";
    private static final String SKELETON = "minecraft:skeleton";
    private static final IntentActionExecutor EXECUTOR = new IntentActionExecutor();

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void execute_Outcome_ProducesExpectedIntent(Row row) {
        ExecutionResult result = EXECUTOR.execute(row.outcome(), row.context());

        assertEquals(row.expected(), result,
                "Исполнение решения «" + row.name() + "»: бой бьёт или стреляет по ближайшей видимой цели, отход "
                        + "двигается или строит по способу, игнор и отход без способа — простой");
    }

    static Stream<Row> rows() {
        EnemyFact near = AdapterFixtures.enemy(ZOMBIE, "z1", 4, 0);
        EnemyFact close = AdapterFixtures.enemy(SKELETON, "s1", 2, 0);
        List<EnemyFact> one = List.of(near);
        return Stream.of(
                new Row("бой без цели", AdapterFixtures.outcome(TacticalOption.FIGHT, TacticalAction.ENGAGE,
                        RetreatMethod.NONE), AdapterFixtures.context(List.of()), ExecutionResult.idle()),
                new Row("бой в ближнем бою", AdapterFixtures.outcome(TacticalOption.FIGHT, TacticalAction.ENGAGE,
                        RetreatMethod.NONE), AdapterFixtures.context(one), new ExecutionResult.Attacking(near)),
                new Row("стрельба", AdapterFixtures.outcome(TacticalOption.FIGHT, TacticalAction.SHOOT,
                        RetreatMethod.NONE), AdapterFixtures.context(one), new ExecutionResult.Shooting(near)),
                new Row("защита зоны", AdapterFixtures.outcome(TacticalOption.FIGHT, TacticalAction.DEFEND_AREA,
                        RetreatMethod.NONE), AdapterFixtures.context(one), new ExecutionResult.Attacking(near)),
                new Row("охрана подопечного", AdapterFixtures.outcome(TacticalOption.FIGHT, TacticalAction.PROTECT,
                        RetreatMethod.NONE), AdapterFixtures.context(one), new ExecutionResult.Attacking(near)),
                new Row("отход домой", AdapterFixtures.outcome(TacticalOption.RETREAT, TacticalAction.RETREAT,
                        RetreatMethod.HOME), AdapterFixtures.context(one), new ExecutionResult.Moving(RetreatMethod.HOME)),
                new Row("отход в укрытие", AdapterFixtures.outcome(TacticalOption.RETREAT, TacticalAction.RETREAT,
                        RetreatMethod.COVER), AdapterFixtures.context(one),
                        new ExecutionResult.Moving(RetreatMethod.COVER)),
                new Row("кайт", AdapterFixtures.outcome(TacticalOption.RETREAT, TacticalAction.RETREAT,
                        RetreatMethod.KITE), AdapterFixtures.context(one), new ExecutionResult.Moving(RetreatMethod.KITE)),
                new Row("отход к воде", AdapterFixtures.outcome(TacticalOption.RETREAT, TacticalAction.RETREAT,
                        RetreatMethod.WATER), AdapterFixtures.context(one),
                        new ExecutionResult.Moving(RetreatMethod.WATER)),
                new Row("баррикада", AdapterFixtures.outcome(TacticalOption.RETREAT, TacticalAction.RETREAT,
                        RetreatMethod.BARRICADE), AdapterFixtures.context(one),
                        new ExecutionResult.Building(RetreatMethod.BARRICADE)),
                new Row("столб", AdapterFixtures.outcome(TacticalOption.RETREAT, TacticalAction.RETREAT,
                        RetreatMethod.PILLAR), AdapterFixtures.context(one),
                        new ExecutionResult.Building(RetreatMethod.PILLAR)),
                new Row("отход прочь", AdapterFixtures.outcome(TacticalOption.RETREAT, TacticalAction.RETREAT,
                        RetreatMethod.AWAY), AdapterFixtures.context(one), new ExecutionResult.Moving(RetreatMethod.AWAY)),
                new Row("отход без способа", AdapterFixtures.outcome(TacticalOption.RETREAT, TacticalAction.RETREAT,
                        RetreatMethod.NONE), AdapterFixtures.context(one), ExecutionResult.idle()),
                new Row("игнор", AdapterFixtures.outcome(TacticalOption.IGNORE, TacticalAction.CONTINUE,
                        RetreatMethod.NONE), AdapterFixtures.context(one), ExecutionResult.idle()),
                new Row("ближайшая видимая цель", AdapterFixtures.outcome(TacticalOption.FIGHT, TacticalAction.ENGAGE,
                        RetreatMethod.NONE), AdapterFixtures.context(List.of(near, close)),
                        new ExecutionResult.Attacking(close)),
                new Row("невидимую цель не берём", AdapterFixtures.outcome(TacticalOption.FIGHT, TacticalAction.ENGAGE,
                        RetreatMethod.NONE),
                        AdapterFixtures.context(List.of(AdapterFixtures.enemy(ZOMBIE, "z2", 1, 0, false), near)),
                        new ExecutionResult.Attacking(near)),
                new Row("цель, бьющая духа, важнее ближайшей мирной", AdapterFixtures.outcome(TacticalOption.FIGHT,
                        TacticalAction.ENGAGE, RetreatMethod.NONE),
                        AdapterFixtures.context(List.of(new EnemyFact(ZOMBIE, "idle", new Point(1, 0, 0), 1, 0, true, false),
                                near)),
                        new ExecutionResult.Attacking(near)));
    }

    @Test
    void execute_NullOutcome_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> EXECUTOR.execute(null, AdapterFixtures.context(List.of())),
                "Решение без исхода — ошибка вызова, а не простой: тихий пропуск скрыл бы неверный код исполнения");

        assertTrue(error.getMessage().contains("outcome"),
                "Сообщение называет исход; получили: " + error.getMessage());
    }

    @Test
    void execute_NullContext_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> EXECUTOR.execute(AdapterFixtures.outcome(TacticalOption.IGNORE, TacticalAction.CONTINUE,
                        RetreatMethod.NONE), null),
                "Решение без контекста не знает цели — ошибка вызова, а не простой");

        assertTrue(error.getMessage().contains("context"),
                "Сообщение называет контекст; получили: " + error.getMessage());
    }

    @Test
    void moving_NoneMethod_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ExecutionResult.Moving(RetreatMethod.NONE),
                "Движение без способа отхода бессмысленно: вариант обязан отказаться, а не тащить NONE в мир");

        assertTrue(error.getMessage().contains("NONE"),
                "Сообщение называет запрещённый способ; получили: " + error.getMessage());
    }

    @Test
    void attacking_NullEnemy_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ExecutionResult.Attacking(null),
                "Атака без врага бессмысленна: вариант обязан отказаться, а не тащить null в мир");

        assertTrue(error.getMessage().contains("Цель"),
                "Сообщение называет цель; получили: " + error.getMessage());
    }

    private record Row(String name, TacticalOutcome outcome, SituationContext context, ExecutionResult expected) {
        @Override
        public String toString() {
            return name;
        }
    }
}

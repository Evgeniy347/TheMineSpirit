package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnvFacts;
import com.theminespirit.core.tactics.Orders;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.TacticsEngine;
import com.theminespirit.core.tactics.TacticsMemory;
import com.theminespirit.core.tactics.TimeOfDay;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ForcedRetreatTests {
    private static final String SPIRIT_ID = "6f1c2d3e-0000-4000-8000-000000000002";
    private static final Point ZONE = new Point(0, 0, 0);
    private static final CombatDirective GUARD_POST = new CombatDirective(
            CombatMode.GUARD_AREA, Optional.empty(), Optional.of(RetreatMethod.HOME), Optional.of(ZONE));
    private static final DecisionOrder ORDER =
            DecisionOrder.of(CombatMode.GUARD_AREA, true, Optional.empty(), Optional.empty());

    private static Situation situation(List<EnemyFact> enemies) {
        return new Situation(
                AdapterFixtures.self(),
                enemies,
                AdapterFixtures.env(TimeOfDay.DAY),
                new Orders(CombatMode.GUARD_AREA, ZONE, 10, null, false, false),
                0,
                0);
    }

    private static Situation creeperSituation() {
        EnvFacts withCover = new EnvFacts(
                EnvFacts.FAR_DISTANCE, 0, 15, TimeOfDay.DAY, EnvFacts.FAR_DISTANCE, false, 4, EnvFacts.FAR_DISTANCE);
        return new Situation(
                AdapterFixtures.self(),
                List.of(AdapterFixtures.enemy("minecraft:creeper", "creeper", 3, 0)),
                withCover,
                new Orders(CombatMode.GUARD_AREA, ZONE, 10, null, false, false),
                0,
                0);
    }

    private static DecisionObservation observe(TacticalOutcome outcome, Situation situation, long tick) {
        return new DecisionObservation(
                tick, SPIRIT_ID, "spirit", outcome, Optional.empty(), ORDER, DecisionFacts.of(situation));
    }

    private static TacticalOutcome forced(TacticalOutcome outcome, CombatDirective directive) {
        return ForcedRetreat.apply(outcome, directive, RetreatMethod.NONE);
    }

    @Test
    void apply_GuardPostWithoutEnemies_KeepsIgnoreAndDoesNotTouchJournal() {
        TacticsEngine engine = new TacticsEngine(TacticsConfig.loadDefault());
        Situation creeper = creeperSituation();
        Situation empty = situation(List.of());
        DecisionJournal journal =
                new DecisionJournal(8, TacticsAdapterSettings.defaults().journalMaxPerSecond());

        TacticalOutcome threat = engine.step(creeper, TacticsMemory.initial(CombatMode.GUARD_AREA, 0, 1), 0);
        assertEquals(
                TacticalOption.RETREAT,
                threat.decision(),
                "Сцена должна начинаться с отхода: иначе журналу нечего менять");
        journal.offer(observe(threat, creeper, 0));

        List<TacticalOption> decisions = new ArrayList<>();
        TacticsMemory memory = TacticsMemory.initial(CombatMode.GUARD_AREA, 0, 1);
        for (long tick = 10; tick <= 200; tick += 10) {
            TacticalOutcome next = engine.step(empty, memory, tick);
            memory = next.memory();
            TacticalOutcome effective = forced(next, GUARD_POST);
            decisions.add(effective.decision());
            journal.offer(observe(effective, empty, tick));
        }

        assertTrue(
                decisions.stream().allMatch(option -> option == TacticalOption.IGNORE),
                "Пост охраны без врагов не приказывает отступать: решение остаётся IGNORE, получено "
                        + decisions.stream().distinct().toList());
        assertEquals(
                1,
                journal.last(10).size(),
                "Смена RETREAT → IGNORE пишется один раз, дальше решение не меняется: журнал " + journal.last(10));
        assertEquals(
                List.of(DecisionSnapshot.DECISION, TacticalOption.RETREAT.name(), TacticalOption.IGNORE.name()),
                List.of(
                        journal.last(1).get(0).changed(),
                        journal.last(1).get(0).before(),
                        journal.last(1).get(0).after()),
                "Единственная запись журнала — прежний отход и стабильный игнор");
    }

    @Test
    void apply_GuardPostWhenEngineAlreadyRetreats_KeepsPostRetreatMethod() {
        Situation creeper = creeperSituation();
        TacticalOutcome outcome = new TacticsEngine(TacticsConfig.loadDefault())
                .step(creeper, TacticsMemory.initial(CombatMode.GUARD_AREA, 0, 1), 10);
        assertEquals(TacticalOption.RETREAT, outcome.decision(), "Крипер в 3 блоках обязан отвести духа");

        TacticalOutcome effective = ForcedRetreat.apply(outcome, GUARD_POST, RetreatMethod.HOME);

        assertEquals(
                RetreatMethod.HOME,
                effective.retreatMethod(),
                "Пост охраны задаёт способ отхода HOME, когда движок сам решил отступать");
    }

    @Test
    void apply_GuardPostWithEnemyOutsideLeash_KeepsRuleIgnore() {
        TacticsEngine engine = new TacticsEngine(TacticsConfig.loadDefault());
        Situation outside = situation(List.of(AdapterFixtures.enemy("minecraft:zombie", "z", 20, 0)));

        TacticalOutcome outcome = engine.step(outside, TacticsMemory.initial(CombatMode.GUARD_AREA, 0, 1), 10);
        TacticalOutcome effective = forced(outcome, GUARD_POST);

        assertEquals(
                List.of(TacticalOption.IGNORE, "outside_leash_ignore", TacticalOption.IGNORE),
                List.of(outcome.decision(), outcome.trace().rule(), effective.decision()),
                "Враг за поводком охраны не превращает пост в приказ отступать");
        assertEquals(outcome.trace(), effective.trace(), "Решение не менялось, поэтому и обоснование остаётся прежним");
    }

    @Test
    void apply_ExplicitFleeWithEnemies_StillForcesRetreat() {
        Situation zombie = AdapterFixtures.collect(List.of(AdapterFixtures.enemy("minecraft:zombie", "z", 5, 0)));
        TacticalOutcome outcome = new TacticsEngine(TacticsConfig.loadDefault())
                .step(zombie, TacticsMemory.initial(CombatMode.AGGRESSIVE, 0, 1), 10);
        assertNotEquals(
                TacticalOption.RETREAT, outcome.decision(), "Обычный бой не отход: иначе проверка ничего не значит");

        TacticalOutcome effective = forced(outcome, CombatDirective.flee());

        assertEquals(
                List.of(TacticalOption.RETREAT, RetreatMethod.AWAY),
                List.of(effective.decision(), effective.retreatMethod()),
                "Явный приказ бежать при врагах по-прежнему отводит духа");
    }

    @Test
    void apply_ExplicitFleeWithoutEnemies_StillForcesRetreat() {
        Situation empty = situation(List.of());
        TacticalOutcome outcome = new TacticsEngine(TacticsConfig.loadDefault())
                .step(empty, TacticsMemory.initial(CombatMode.PASSIVE, 0, 1), 10);

        TacticalOutcome effective = forced(outcome, CombatDirective.flee());

        assertEquals(
                TacticalOption.RETREAT,
                effective.decision(),
                "Явный приказ бежать исполняется и без врагов: это команда, а не выбор тактики");
    }
}

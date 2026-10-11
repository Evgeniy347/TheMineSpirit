package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TacticsEngineTests {
    private static final TacticsConfig CONFIG = TacticsConfig.loadDefault();

    private static TacticsEngine engine() {
        return new TacticsEngine(CONFIG);
    }

    private static TacticalOutcome first(Situation situation) {
        return engine().step(situation, Memories.fresh(situation), 0);
    }

    @Test
    void step_LoneZombieFullHp_FightsAndRemembersAnchor() {
        Situation situation = SituationBuilder.create().enemy("zombie", 5).build();

        TacticalOutcome outcome = first(situation);

        assertEquals(List.of(TacticalOption.FIGHT, TacticalAction.ENGAGE, RetreatMethod.NONE, TacticalState.ENGAGED,
                CombatMode.AGGRESSIVE), List.of(outcome.decision(), outcome.action(), outcome.retreatMethod(),
                outcome.state(), outcome.mode()),
                "Один зомби при полном HP и мече: бой, обычная атака, отход не нужен, состояние ENGAGED");
        assertEquals(List.of(Decision.BY_SCORE, "engage_default", Optional.of("calm_to_engaged"), ""),
                List.of(outcome.trace().rule(), outcome.trace().actionRule(), outcome.trace().stateRule(),
                        outcome.trace().whyNotFight()),
                "Trace называет, чем решено: оценка, затем действие по умолчанию и переход CALM → ENGAGED");
        assertEquals(Optional.of(situation.self().pos()), outcome.memory().anchor(),
                "Бой начался: точка начала боя запоминается якорем для оборонительного режима");
        assertEquals(Factor.values().length, outcome.trace().factors().size(),
                "Trace перечисляет вклад каждого из факторов");
    }

    @Test
    void step_CreeperCloseWithCoverAndBlocks_RetreatsByHardRuleToCover() {
        Situation situation = SituationBuilder.create().enemy("creeper", 3).cover(4).blocks(10).build();

        TacticalOutcome outcome = first(situation);

        assertEquals(List.of(TacticalOption.RETREAT, TacticalAction.RETREAT, RetreatMethod.COVER,
                TacticalState.RETREATING, "explosive_close_retreat", "решило правило explosive_close_retreat"),
                List.of(outcome.decision(), outcome.action(), outcome.retreatMethod(), outcome.state(),
                        outcome.trace().rule(), outcome.trace().whyNotFight()),
                "Крипер в 3 блоках: жёсткое правило отхода, укрытие и столб равны по полезности, выигрывает укрытие "
                        + "по порядку способов, баррикада закрыта взрывчаткой");
    }

    @Test
    void step_NoEnemies_IgnoresAndStaysCalm() {
        TacticalOutcome outcome = first(SituationBuilder.create().build());

        assertEquals(List.of(TacticalOption.IGNORE, TacticalAction.CONTINUE, TacticalState.CALM,
                "no_enemies_ignore", Optional.empty()),
                List.of(outcome.decision(), outcome.action(), outcome.state(), outcome.trace().rule(),
                        outcome.returnTo()),
                "Без врагов: игнор, продолжать прежнее дело, покой, возвращаться некуда");
    }

    @Test
    void step_GuardAreaEnemyBeyondLeash_IgnoresAndReturnsToZoneCenter() {
        Situation situation = SituationBuilder.create().mode(CombatMode.GUARD_AREA).pos(12, 0).zone(0, 0, 10)
                .enemy("zombie", 20).build();

        TacticalOutcome outcome = first(situation);

        assertEquals(List.of(TacticalOption.IGNORE, "outside_leash_ignore", Optional.of(new Point(0, 0, 0))),
                List.of(outcome.decision(), outcome.trace().rule(), outcome.returnTo()),
                "Враг за поводком охраны (пределы зоны 10 + 4): не преследуем и возвращаемся к центру зоны");
    }

    @Test
    void step_AggressiveWithLowHp_DegradesModeAndRestoresItWhenHealthy() {
        Situation hurt = SituationBuilder.create().hpFraction(0.28).enemy("zombie", 10).cover(5).build();
        Situation healed = SituationBuilder.create().hpFraction(0.8).enemy("zombie", 10).cover(5).build();
        TacticsEngine engine = engine();

        TacticalOutcome low = engine.step(hurt, Memories.fresh(hurt), 0);
        TacticalOutcome recovered = engine.step(healed, low.memory(), 10);

        assertEquals(List.of(CombatMode.DEFENSIVE, CombatMode.AGGRESSIVE, CombatMode.AGGRESSIVE,
                Optional.of("aggressive_tires"), Optional.of("aggressive_recovers")),
                List.of(low.mode(), low.memory().orderedMode(), recovered.mode(), low.trace().modeRule(),
                        recovered.trace().modeRule()),
                "Приказ aggressive не теряется: при HP 28 % режим временно defensive, при 80 % возвращается");
    }

    @Test
    void step_EnemyGoneForLongEnough_ReturnsToCalmAndDropsAnchor() {
        Situation fight = SituationBuilder.create().enemy("zombie", 5).build();
        Situation clear = SituationBuilder.create().build();
        TacticsEngine engine = engine();
        TacticalOutcome engaged = engine.step(fight, Memories.fresh(fight), 0);

        TacticalOutcome stillEngaged = engine.step(clear, engaged.memory(), 10);
        TacticalOutcome calm = engine.step(clear, stillEngaged.memory(), 30);

        assertEquals(List.of(TacticalState.ENGAGED, TacticalState.CALM, Optional.of("engaged_to_calm"),
                Optional.empty()),
                List.of(stillEngaged.state(), calm.state(), calm.trace().stateRule(), calm.memory().anchor()),
                "Враг пропал: через 10 тиков бой ещё не окончен, через 30 (≥ 20) — покой, якорь забыт");
    }

    @Test
    void step_HordeOnScore_ExplainsWhyNotFightWithTopFactors() {
        Situation situation = SituationBuilder.create().enemies("zombie", 8, 6).cover(5).build();

        TacticalOutcome outcome = first(situation);

        assertEquals(TacticalOption.RETREAT, outcome.decision(), "Шестеро зомби при одном мече: отход по оценке");
        String why = outcome.trace().whyNotFight();
        assertTrue(why.startsWith("перевес RETREAT над FIGHT:") && why.contains("WIN_MARGIN"),
                "Trace объясняет, почему не бой: называет факторы, которые перевесили (WIN_MARGIN здесь главный), а "
                        + "получили: " + why);
        assertTrue(outcome.trace().explain().contains(why),
                "Текстовое объяснение содержит ту же причину, что и поле trace");
    }

    @Test
    void step_SameInputTwice_GivesEqualOutcomesIncludingTrace() {
        Situation situation = SituationBuilder.create().enemies("zombie", 6, 3).enemy("skeleton", 12).cover(5).build();
        TacticsMemory memory = Memories.fresh(situation);

        TacticalOutcome one = engine().step(situation, memory, 7);
        TacticalOutcome two = engine().step(situation, memory, 7);

        assertEquals(one, two, "Вызов без побочных эффектов: те же факты и память дают побитово тот же результат");
    }

    @Test
    void step_NegativeTick_IsRejected() {
        Situation situation = SituationBuilder.create().build();

        assertThrows(IllegalArgumentException.class, () -> engine().step(situation, Memories.fresh(situation), -1),
                "Номер тика обязан быть неотрицательным: отрицательный означает ошибку адаптера, а не момент до начала мира");
    }
}

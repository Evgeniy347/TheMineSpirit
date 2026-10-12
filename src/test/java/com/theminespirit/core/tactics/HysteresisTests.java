package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HysteresisTests {
    private static final TacticsConfig CONFIG = TacticsConfig.loadDefault();
    private static final int INTERVAL = 10;

    private static Decision scored(TacticalOption option) {
        return new Decision(option, Decision.BY_SCORE, Set.of(), List.of(), HoldStatus.NONE);
    }

    private static SituationBuilder zombies(int count, double hpFraction) {
        return SituationBuilder.create().hpFraction(hpFraction).enemies("zombie", 8, count);
    }

    private static Situation noisyEnemy(double enemyHp, double selfHp) {
        return SituationBuilder.create().hpFraction(selfHp)
                .enemy(new EnemyFact("minecraft:zombie", "z", new Point(8, 0, 0), enemyHp, 0, true, true)).build();
    }

    private static double fightMinusRetreat(Situation situation) {
        Scores scores = new Scorer().score(situation, Assessments.full(situation), CONFIG.weights());
        return scores.total(TacticalOption.FIGHT) - scores.total(TacticalOption.RETREAT);
    }

    private static double borderlineSelfHp() {
        double bestHp = 0;
        double bestGap = Double.MAX_VALUE;
        for (int step = 300; step <= 1000; step++) {
            double gap = Math.abs(fightMinusRetreat(noisyEnemy(0.9, step / 1000.0)));
            if (gap < bestGap) {
                bestGap = gap;
                bestHp = step / 1000.0;
            }
        }
        assertTrue(bestGap < 0.03,
                "В тесте нет пограничной ситуации (разность FIGHT и RETREAT " + bestGap + "): подберите сетку заново");
        return bestHp;
    }

    private static int changes(TacticsConfig config, double selfHp) {
        DecisionRun run = new DecisionRun(config, noisyEnemy(0.9, selfHp), 1);
        TacticalOption last = null;
        int changes = 0;
        for (int tick = 0; tick < 400; tick += INTERVAL) {
            double enemyHp = (tick / INTERVAL) % 2 == 0 ? 0.855 : 0.945;
            TacticalOption now = run.step(noisyEnemy(enemyHp, selfHp), tick).option();
            if (last != null && now != last) {
                changes++;
            }
            last = now;
        }
        return changes;
    }

    private static TacticsConfig withoutHysteresis() {
        String weights = TacticsTexts.mutate(TacticsTexts.mutate(TacticsTexts.read("weights.json"),
                "\"holdTicks\": 40", "\"holdTicks\": 0"), "\"stickiness\": 0.25", "\"stickiness\": 0");
        return TacticsConfig.fromJson(TacticsTexts.read("enemies.json"), weights, TacticsTexts.read("rules.json"),
                TacticsTexts.read("modes.json"));
    }

    @Test
    void decisions_NoiseOnBorderline_SwitchNoMoreOftenThanOncePerWindow() {
        double selfHp = borderlineSelfHp();

        int withHysteresis = changes(CONFIG, selfHp);
        int without = changes(withoutHysteresis(), selfHp);

        assertTrue(withHysteresis <= 1,
                "Шум фактов ±5 % на границе FIGHT/RETREAT не должен переключать решение чаще раза за окно удержания: "
                        + withHysteresis + " переключений за 400 тиков");
        assertTrue(without >= 5,
                "Без удержания и бонуса тот же шум дрожит решением (" + without + " переключений): иначе тест гистерезиса "
                        + "не отличает его от отсутствия");
    }

    @Test
    void decisions_ChangedFactsInsideWindow_KeepPreviousUntilWindowEnds() {
        Situation calm = zombies(1, 1).build();
        Situation horde = zombies(6, 1).build();
        DecisionRun run = new DecisionRun(CONFIG, calm, 1);
        Decision first = run.step(calm, 0);

        Decision inside = run.step(horde, 39);
        Decision outside = run.step(horde, 40);

        assertEquals(List.of(TacticalOption.FIGHT, TacticalOption.RETREAT, TacticalOption.FIGHT, HoldStatus.HELD,
                TacticalOption.RETREAT), List.of(first.option(), new DecisionRun(CONFIG, horde, 1).step(horde, 0).option(), inside.option(),
                inside.hold(), outside.option()),
                "Через 39 тиков орда ещё не меняет решение (окно 40), через 40 — меняет. Орда без истории даёт RETREAT, значит "
                        + "решение держит именно окно удержания");
    }

    @Test
    void decisions_HpDropOverThreshold_ReleasesHoldInsideWindow() {
        Situation calm = zombies(1, 1).build();
        DecisionRun heavy = new DecisionRun(CONFIG, calm, 1);
        DecisionRun light = new DecisionRun(CONFIG, calm, 1);
        heavy.step(calm, 0);
        light.step(calm, 0);

        Decision big = heavy.step(zombies(6, 0.65).build(), 10);
        Decision small = light.step(zombies(6, 0.75).build(), 10);

        assertEquals(List.of(HoldStatus.RELEASED_HP_DROP, TacticalOption.RETREAT, HoldStatus.HELD,
                TacticalOption.FIGHT), List.of(big.hold(), big.option(), small.hold(), small.option()),
                "Потеря 35 % HP за окно снимает удержание, потеря 25 % — нет: порог hpDropRelease = 0.3");
    }

    @Test
    void decisions_HardRuleInsideWindow_OverridesHold() {
        Situation calm = zombies(1, 1).build();
        DecisionRun run = new DecisionRun(CONFIG, calm, 1);
        run.step(calm, 0);

        Decision critical = run.step(zombies(1, 0.2).build(), 10);

        assertEquals(List.of(TacticalOption.RETREAT, "critical_hp_retreat", HoldStatus.RELEASED_BY_RULE),
                List.of(critical.option(), critical.rule(), critical.hold()),
                "Жёсткое правило снимает удержание сразу: критическое HP не ждёт конца окна");
    }

    @Test
    void decisions_PreviousBecomesExcluded_HoldIsReleased() {
        Situation calm = zombies(1, 1).build();
        DecisionRun run = new DecisionRun(CONFIG, calm, 1);
        run.step(calm, 0);

        Decision nearLava = run.step(SituationBuilder.create().lava(1).enemies("zombie", 8, 1).build(), 10);

        assertEquals(List.of(HoldStatus.RELEASED_EXCLUDED, true),
                List.of(nearLava.hold(), nearLava.option() != TacticalOption.FIGHT),
                "Удерживать вычеркнутый вариант нельзя: у лавы бой запрещён, даже если решение недавно принято");
    }

    @Test
    void decisions_AfterWindowOnBorderline_PreviousOptionKeepsStickinessBonus() {
        Situation border = noisyEnemy(0.9, borderlineSelfHp());
        Scores scores = new Scorer().score(border, Assessments.full(border), CONFIG.weights());
        Decider decider = new Decider(new HardRules(CONFIG.rules().hard(), new RuleCounter()), CONFIG.weights());
        FactMap facts = FactMap.from(border, Assessments.full(border));

        Decision keepsFight = decider.decide(facts, scores, 1, Memories.holding(border, TacticalOption.FIGHT, 0), 40);
        Decision keepsRetreat = decider.decide(facts, scores, 1, Memories.holding(border, TacticalOption.RETREAT, 0),
                40);

        assertEquals(List.of(TacticalOption.FIGHT, TacticalOption.RETREAT),
                List.of(keepsFight.option(), keepsRetreat.option()),
                "После окна прежнее решение получает +stickiness (0.25) к счёту: на границе оно сохраняется в обе стороны");
    }

    @Test
    void step_NewZombieTenTicksAfterNoEnemiesRule_FightsInsteadOfHoldingIgnore() {
        TacticsEngine engine = new TacticsEngine(CONFIG);
        Situation empty = SituationBuilder.create().build();
        Situation zombie = SituationBuilder.create().enemy("zombie", 3).cover(6).build();
        TacticalOutcome quiet = engine.step(empty, Memories.fresh(empty), 1000);

        TacticalOutcome attacked = engine.step(zombie, quiet.memory(), 1010);

        assertEquals(List.of(TacticalOption.IGNORE, "no_enemies_ignore", TacticalOption.FIGHT, Decision.BY_SCORE,
                HoldStatus.NONE), List.of(quiet.decision(), quiet.trace().rule(), attacked.decision(),
                attacked.trace().rule(), attacked.trace().hold()),
                "Решение жёсткого правила «врагов нет» не удерживается: гистерезис гасит дрожь оценки, а не переносит "
                        + "IGNORE на ситуацию, где появился зомби в 3 блоках и aggressive-дух обязан ответить. Если "
                        + "ожидание меняют осознанно, пересмотрите Hysteresis.status и TacticsMemory.previousSource");
    }

    @Test
    void decide_PreviousFromRuleInsideWindow_EqualsDecisionWithoutMemory() {
        Situation zombie = SituationBuilder.create().enemy("zombie", 3).build();
        Decider decider = new Decider(new HardRules(CONFIG.rules().hard(), new RuleCounter()), CONFIG.weights());
        Assessment assessment = Assessments.withRoutes(zombie, 1);
        Scores scores = new Scorer().score(zombie, assessment, CONFIG.weights());
        FactMap facts = FactMap.from(zombie, assessment);

        Decision afterRule = decider.decide(facts, scores, 1, Memories.decidedByRule(zombie, TacticalOption.IGNORE, 990),
                1000);
        Decision withoutMemory = decider.decide(facts, scores, 1, Memories.fresh(zombie), 1000);

        assertEquals(List.of(withoutMemory.option(), withoutMemory.rule(), HoldStatus.NONE),
                List.of(afterRule.option(), afterRule.rule(), afterRule.hold()),
                "Прежнее решение принято правилом, а не счётом: удержание и бонус stickiness ему не положены, решение "
                        + "равно решению без памяти");
    }

    @Test
    void remember_ScoreDecisionAfterRuleWithSameOption_StartsHoldWindowFromNow() {
        TacticsMemory afterRule = new Hysteresis(CONFIG.weights()).remember(
                TacticsMemory.initial(CombatMode.AGGRESSIVE, 0, 1.0),
                new Decision(TacticalOption.IGNORE, "no_enemies_ignore", Set.of(), List.of(), HoldStatus.NONE), 10, 1.0);

        TacticsMemory afterScore = new Hysteresis(CONFIG.weights()).remember(afterRule, scored(TacticalOption.IGNORE),
                30, 1.0);

        assertEquals(List.of(DecisionSource.RULE, 10L, DecisionSource.SCORE, 30L),
                List.of(afterRule.previousSource(), afterRule.decidedAt(), afterScore.previousSource(),
                        afterScore.decidedAt()),
                "Окно удержания отсчитывается от решения по счёту: тот же вариант, принятый счётом после правила, "
                        + "начинает окно заново, иначе удержание кончится раньше времени");
    }

    @Test
    void decide_SameInputAndMemory_GivesSameOutput() {
        Situation situation = zombies(3, 0.8).build();
        TacticsMemory memory = Memories.holding(situation, TacticalOption.FIGHT, 5);
        Decider decider = new Decider(new HardRules(CONFIG.rules().hard(), new RuleCounter()), CONFIG.weights());
        Assessment assessment = Assessments.full(situation);
        Scores scores = new Scorer().score(situation, assessment, CONFIG.weights());
        FactMap facts = FactMap.from(situation, assessment);

        Decision first = decider.decide(facts, scores, 1, memory, 20);
        Decision second = decider.decide(facts, scores, 1, memory, 20);

        assertEquals(first, second, "Память неизменяема, решение детерминировано: тот же вход и та же память дают то же решение");
    }

    @Test
    void remember_SameOptionAgain_KeepsDecisionTimeAndRollsHpWindow() {
        TacticsMemory memory = TacticsMemory.initial(CombatMode.AGGRESSIVE, 0, 1.0);
        Hysteresis hysteresis = new Hysteresis(CONFIG.weights());

        TacticsMemory first = hysteresis.remember(memory, scored(TacticalOption.FIGHT), 10, 0.9);
        TacticsMemory same = hysteresis.remember(first, scored(TacticalOption.FIGHT), 30, 0.8);
        TacticsMemory rolled = hysteresis.remember(same, scored(TacticalOption.FIGHT), 40, 0.7);
        TacticsMemory changed = hysteresis.remember(rolled, scored(TacticalOption.RETREAT), 50, 0.7);

        assertEquals(List.of(Optional.of(TacticalOption.FIGHT), 10L, 1.0, 0L, 0.7, 40L, 50L),
                List.of(first.previous(), same.decidedAt(), same.hpWindowRef(), same.hpWindowStart(),
                        rolled.hpWindowRef(), rolled.hpWindowStart(), changed.decidedAt()),
                "Время решения обновляется только при смене варианта, окно HP перекатывается каждые holdTicks");
        assertNotEquals(first, changed, "Память после смены решения отличается от прежней");
    }
}

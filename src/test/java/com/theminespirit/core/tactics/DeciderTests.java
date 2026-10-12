package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeciderTests {
    private static final String LAVA_RULE = "\"then\": {\"exclude\": [\"FIGHT\"]}";

    private static Decision decide(TacticsConfig config, Situation situation, int routes) {
        Assessment assessment = Assessments.full(situation).withEscapeRoutes(routes);
        Scores scores = new Scorer().score(situation, assessment, config.weights());
        Decider decider = new Decider(new HardRules(config.rules().hard(), new RuleCounter()), config.weights());
        return decider.decide(FactMap.from(situation, assessment), scores, routes, Memories.fresh(situation), 0);
    }

    private static TacticsConfig lavaExcluding(String options) {
        String rules = TacticsTexts.mutate(TacticsTexts.read("rules.json"), LAVA_RULE,
                "\"then\": {\"exclude\": " + options + "}");
        return TacticsConfig.fromJson(TacticsTexts.read("enemies.json"), TacticsTexts.read("weights.json"), rules,
                TacticsTexts.read("modes.json"));
    }

    private static Situation onLava() {
        return SituationBuilder.create().lava(1).enemy("zombie", 8).build();
    }

    @Test
    void decide_NoHardRuleMatches_ChoosesByScore() {
        Decision decision = decide(TacticsConfig.loadDefault(), SituationBuilder.create().enemy("zombie", 8).build(), 1);

        assertEquals(List.of(TacticalOption.FIGHT, Decision.BY_SCORE), List.of(decision.option(), decision.rule()),
                "Один зомби при полном HP — обычная оценка без жёстких правил: бой, победа по счёту");
    }

    @Test
    void decide_ExcludeLeavesOneOption_ThatOptionIsChosen() {
        Decision decision = decide(lavaExcluding("[\"FIGHT\", \"IGNORE\"]"), onLava(), 1);

        assertEquals(List.of(TacticalOption.RETREAT, Decision.SOLE_CANDIDATE, Set.of(TacticalOption.FIGHT,
                TacticalOption.IGNORE)), List.of(decision.option(), decision.rule(), decision.excluded()),
                "Когда вычеркнуты два варианта из трёх, остаётся один и выбирается без оценки");
    }

    @Test
    void decide_ExcludeLeavesTwoOptions_BestOfRemainingWins() {
        Decision decision = decide(TacticsConfig.loadDefault(), onLava(), 1);

        assertEquals(List.of(Decision.BY_SCORE, Set.of(TacticalOption.FIGHT)),
                List.of(decision.rule(), decision.excluded()),
                "Лава рядом вычёркивает FIGHT, а выбор между отходом и игнором идёт по счёту");
        assertEquals(false, decision.option() == TacticalOption.FIGHT,
                "Драться у лавы нельзя ни при каких весах: вычеркнутый вариант не может победить");
    }

    @Test
    void decide_AllOptionsExcludedAndRetreatPossible_RetreatIsChosen() {
        Decision decision = decide(lavaExcluding("[\"FIGHT\", \"RETREAT\", \"IGNORE\"]"), onLava(), 2);

        assertEquals(List.of(TacticalOption.RETREAT, Decision.ALL_EXCLUDED),
                List.of(decision.option(), decision.rule()),
                "Если вычеркнуто всё и есть куда отходить, дух отступает: это самый безопасный остаток");
    }

    @Test
    void decide_AllOptionsExcludedAndNoRetreat_FightIsChosen() {
        Decision decision = decide(lavaExcluding("[\"FIGHT\", \"RETREAT\", \"IGNORE\"]"), onLava(), 0);

        assertEquals(List.of(TacticalOption.FIGHT, Decision.ALL_EXCLUDED),
                List.of(decision.option(), decision.rule()),
                "Если вычеркнуто всё и отступать некуда, остаётся драться: выбирать между гибелью и боем не из чего");
    }

    @Test
    void decide_DecisiveRule_CarriesRuleIdAndExcludedFromHigherRules() {
        Situation burning = SituationBuilder.create().enemy("creeper", 4).build();

        Decision decision = decide(TacticsConfig.loadDefault(), burning, 1);

        assertEquals(List.of(TacticalOption.RETREAT, "explosive_close_retreat"),
                List.of(decision.option(), decision.rule()),
                "Решение жёсткого правила называет его id: trace объясняет, какое правило сработало");
    }

    @Test
    void decide_HeldRetreatReplacedWithoutRoute_HasNoHold() {
        TacticsConfig config = TacticsConfig.loadDefault();
        Situation situation = SituationBuilder.create().enemy("zombie", 6).build();
        Assessment assessment = Assessments.full(situation).withEscapeRoutes(0);
        Scores scores = new Scorer().score(situation, assessment, config.weights());
        Decider decider = new Decider(new HardRules(config.rules().hard(), new RuleCounter()), config.weights());

        Decision decision = decider.decide(FactMap.from(situation, assessment), scores, 0,
                Memories.holding(situation, TacticalOption.RETREAT, 0), 10);

        assertEquals(List.of(TacticalOption.FIGHT, "no_route_fight_targeted", HoldStatus.NONE),
                List.of(decision.option(), decision.rule(), decision.hold()),
                "Удерживаемое RETREAT без способа отхода подменено боем: подмена — решение правила, а не удержания, "
                        + "поэтому hold = NONE, а не HELD внутреннего решения");
    }

    @Test
    void decide_NoRouteAndFightExcluded_ReplacesRetreatWithIgnore() {
        Decision decision = decide(TacticsConfig.loadDefault(), SituationBuilder.create().lava(1).enemy("zombie", 6).build(), 0);

        assertEquals(List.of(TacticalOption.IGNORE, "no_route_ignore"), List.of(decision.option(), decision.rule()),
                "Бой у лавы вычеркнут: подмена RETREAT без пути обязана уйти на невычеркнутый вариант IGNORE");
    }
}

package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class DeciderProperties {
    private static final TacticsConfig CONFIG = TacticsConfig.loadDefault();

    @Provide
    Arbitrary<Situation> situations() {
        return Situations.any();
    }

    @Provide
    Arbitrary<List<Double>> weightCells() {
        return Arbitraries.doubles().between(-ScoreWeights.MAX_WEIGHT, ScoreWeights.MAX_WEIGHT)
                .list().ofSize(Factor.values().length * TacticalOption.values().length);
    }

    private static ScoreWeights randomWeights(List<Double> cells) {
        Map<Factor, Map<TacticalOption, Double>> matrix = new EnumMap<>(Factor.class);
        int next = 0;
        for (Factor factor : Factor.values()) {
            Map<TacticalOption, Double> row = new EnumMap<>(TacticalOption.class);
            for (TacticalOption option : TacticalOption.values()) {
                row.put(option, cells.get(next++));
            }
            matrix.put(factor, row);
        }
        ScoreWeights shipped = CONFIG.weights();
        return new ScoreWeights(matrix, shipped.tieBreak(), shipped.stickiness(), shipped.holdTicks(),
                shipped.hpDropRelease(), shipped.scales());
    }

    @Property
    void decide_NoRouteOrScore_NeverPicksExcludedOption(@ForAll("situations") Situation situation,
            @ForAll("weightCells") List<Double> cells, @ForAll boolean cornered, @ForAll TacticalOption previous) {
        ScoreWeights weights = randomWeights(cells);
        Assessment assessment = Assessments.withRoutes(situation, cornered ? 0 : 2);
        FactMap facts = FactMap.from(situation, assessment);
        Scores scores = new Scorer().score(situation, assessment, weights);
        HardRules hardRules = new HardRules(CONFIG.rules().hard(), new RuleCounter());

        Decision decision = new Decider(hardRules, weights).decide(facts, scores, assessment.escapeRoutes(),
                Memories.holding(situation, previous, 0), 1);

        Set<TacticalOption> excluded = EnumSet.noneOf(TacticalOption.class);
        Set<String> mayOverride = new HashSet<>(Set.of(Decision.ALL_EXCLUDED));
        for (HardRule rule : CONFIG.rules().hard()) {
            if (rule.decision().isPresent()) {
                mayOverride.add(rule.id());
            } else if (rule.when().matches(facts)) {
                excluded.addAll(rule.excludes());
            }
        }
        assertEquals(false, excluded.contains(decision.option()) && !mayOverride.contains(decision.rule()),
                "Вычёркнутый вариант " + decision.option() + " выбран правилом " + decision.rule() + " (вычеркнуто "
                        + excluded + "): вычёркивание не обходит ни оценка, ни удержание, ни подмена RETREAT при "
                        + "отсутствии пути; исключения — решающие жёсткие правила и all_excluded");
    }

    private static boolean isNoRoute(String ruleId) {
        return CONFIG.rules().hard().stream()
                .anyMatch(rule -> rule.id().equals(ruleId) && rule.noRouteOption().isPresent());
    }

    @Property
    void decide_DecisiveHardRule_CannotBeOutbidByAnyWeights(@ForAll("situations") Situation situation,
            @ForAll("weightCells") List<Double> cells, @ForAll boolean cornered, @ForAll TacticalOption previous) {
        ScoreWeights weights = randomWeights(cells);
        Assessment assessment = Assessments.withRoutes(situation, cornered ? 0 : 2);
        FactMap facts = FactMap.from(situation, assessment);
        Scores scores = new Scorer().score(situation, assessment, weights);
        HardRules hardRules = new HardRules(CONFIG.rules().hard(), new RuleCounter());

        Decision decision = new Decider(hardRules, weights).decide(facts, scores, assessment.escapeRoutes(), Memories.holding(situation, previous, 0), 1);

        HardVerdict verdict = hardRules.evaluate(facts);
        verdict.decisive().ifPresent(rule -> assertEquals(
                rule.decision().get() == TacticalOption.RETREAT && cornered ? "заменено правилом noRoute" : rule.id(),
                rule.decision().get() == TacticalOption.RETREAT && cornered && isNoRoute(decision.rule())
                        ? "заменено правилом noRoute" : decision.rule(),
                "Жёсткое правило " + rule.id() + " с решением не может быть перебито весами: иначе правила безопасности "
                        + "(критическое HP, крипер вплотную) перестанут быть жёсткими. Проверьте порядок в Decider (единственное исключение — RETREAT без способа отхода, его подменяют правила noRoute)"));
        verdict.excluded().forEach(excluded -> assertEquals(false,
                decision.option() == excluded && verdict.decisive().isEmpty() && decision.rule().equals(Decision.BY_SCORE),
                "Вычеркнутый вариант " + excluded + " не может победить по счёту ни при каких весах"));
    }
}

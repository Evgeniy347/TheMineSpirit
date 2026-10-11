package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    @Property(tries = 200)
    void decide_DecisiveHardRule_CannotBeOutbidByAnyWeights(@ForAll("situations") Situation situation,
            @ForAll("weightCells") List<Double> cells, @ForAll boolean cornered) {
        ScoreWeights weights = randomWeights(cells);
        Assessment assessment = Assessments.full(situation).withEscapeRoutes(cornered ? 0 : 2);
        FactMap facts = FactMap.from(situation, assessment);
        Scores scores = new Scorer().score(situation, assessment, weights);
        HardRules hardRules = new HardRules(CONFIG.rules().hard(), new RuleCounter());

        Decision decision = new Decider(hardRules, weights).decide(facts, scores, assessment.escapeRoutes());

        HardVerdict verdict = hardRules.evaluate(facts);
        verdict.decisive().ifPresent(rule -> assertEquals(rule.decision().get(), decision.option(),
                "Жёсткое правило " + rule.id() + " с решением не может быть перебито весами: иначе правила безопасности "
                        + "(критическое HP, крипер вплотную) перестанут быть жёсткими. Проверьте порядок в Decider"));
        verdict.excluded().forEach(excluded -> assertEquals(false,
                decision.option() == excluded && verdict.decisive().isEmpty() && decision.rule().equals(Decision.BY_SCORE),
                "Вычеркнутый вариант " + excluded + " не может победить по счёту ни при каких весах"));
    }
}

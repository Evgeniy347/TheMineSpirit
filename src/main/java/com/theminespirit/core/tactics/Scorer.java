package com.theminespirit.core.tactics;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class Scorer {
    private final AtomicLong factorEvaluations = new AtomicLong();

    public Scores score(Situation situation, Assessment assessment, ScoreWeights weights) {
        Map<Factor, Double> values = new EnumMap<>(Factor.class);
        for (Factor factor : Factor.values()) {
            values.put(factor, factor.value(situation, assessment, weights.scales()));
            factorEvaluations.incrementAndGet();
        }
        return Scores.of(values, weights);
    }

    public long factorEvaluations() {
        return factorEvaluations.get();
    }

    public static Scores combine(Map<Factor, Double> values, ScoreWeights weights) {
        return Scores.of(values, weights);
    }
}

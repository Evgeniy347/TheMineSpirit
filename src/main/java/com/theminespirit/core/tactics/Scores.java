package com.theminespirit.core.tactics;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record Scores(
        Map<Factor, Double> factors,
        Map<TacticalOption, Map<Factor, Double>> contributions,
        Map<TacticalOption, Double> totals) {
    public static final double TIE_EPSILON = 1e-9;

    public Scores {
        factors = Map.copyOf(factors);
        contributions = Map.copyOf(contributions);
        totals = Map.copyOf(totals);
    }

    public double total(TacticalOption option) {
        return totals.get(option);
    }

    public double factor(Factor factor) {
        return factors.get(factor);
    }

    public double contribution(TacticalOption option, Factor factor) {
        return contributions.get(option).get(factor);
    }

    public TacticalOption best(Set<TacticalOption> candidates, ScoreWeights weights) {
        return best(candidates, weights, Optional.empty());
    }

    public TacticalOption best(Set<TacticalOption> candidates, ScoreWeights weights, Optional<TacticalOption> sticky) {
        TacticalOption best = null;
        for (TacticalOption option : weights.tieBreak()) {
            if (!candidates.contains(option)) {
                continue;
            }
            if (best == null || adjusted(option, sticky, weights) > adjusted(best, sticky, weights) + TIE_EPSILON) {
                best = option;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("Нет кандидатов для выбора решения: " + candidates);
        }
        return best;
    }

    private double adjusted(TacticalOption option, Optional<TacticalOption> sticky, ScoreWeights weights) {
        return total(option) + (sticky.filter(held -> held == option).isPresent() ? weights.stickiness() : 0);
    }

    static Scores of(Map<Factor, Double> values, ScoreWeights weights) {
        Map<TacticalOption, Map<Factor, Double>> contributions = new EnumMap<>(TacticalOption.class);
        Map<TacticalOption, Double> totals = new EnumMap<>(TacticalOption.class);
        for (TacticalOption option : TacticalOption.values()) {
            Map<Factor, Double> row = new EnumMap<>(Factor.class);
            double sum = 0;
            for (Factor factor : Factor.values()) {
                double part = weights.weight(factor, option) * values.get(factor);
                row.put(factor, part);
                sum += part;
            }
            contributions.put(option, row);
            totals.put(option, sum);
        }
        return new Scores(values, contributions, totals);
    }
}

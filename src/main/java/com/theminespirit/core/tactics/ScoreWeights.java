package com.theminespirit.core.tactics;

import static com.theminespirit.core.Lookup.present;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ScoreWeights(
        Map<Factor, Map<TacticalOption, Double>> matrix,
        List<TacticalOption> tieBreak,
        double stickiness,
        int holdTicks,
        double hpDropRelease,
        FactorScales scales) {
    public static final double MAX_WEIGHT = 10;

    public ScoreWeights {
        Map<Factor, Map<TacticalOption, Double>> rows = new EnumMap<>(Factor.class);
        for (Factor factor : Factor.values()) {
            Map<TacticalOption, Double> row = matrix.get(factor);
            if (row == null || row.size() != TacticalOption.values().length) {
                throw new IllegalArgumentException("ScoreWeights.matrix: у фактора " + factor
                        + " нет полной строки весов по вариантам FIGHT/RETREAT/IGNORE: матрица должна быть полной");
            }
            row.forEach((option, weight) ->
                    FactChecks.range("ScoreWeights.weights." + factor + "." + option, weight, -MAX_WEIGHT, MAX_WEIGHT));
            rows.put(factor, Map.copyOf(new EnumMap<>(row)));
        }
        matrix = Map.copyOf(rows);
        tieBreak = List.copyOf(tieBreak);
        if (tieBreak.size() != TacticalOption.values().length
                || tieBreak.stream().distinct().count() != tieBreak.size()) {
            throw new IllegalArgumentException("ScoreWeights.tieBreak = " + tieBreak
                    + ": порядок разрешения ничьей перечисляет каждый вариант ровно один раз");
        }
        FactChecks.range("ScoreWeights.stickiness", stickiness, 0, 2);
        FactChecks.range("ScoreWeights.holdTicks", holdTicks, 0, 2400);
        FactChecks.range("ScoreWeights.hpDropRelease", hpDropRelease, 0.001, 1);
        FactChecks.required("ScoreWeights.scales", scales);
    }

    public double weight(Factor factor, TacticalOption option) {
        return present(present(matrix, factor, "Матрица весов тактики"), option, "Строка весов фактора");
    }
}

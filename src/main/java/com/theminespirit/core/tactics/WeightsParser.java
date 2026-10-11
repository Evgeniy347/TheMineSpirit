package com.theminespirit.core.tactics;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class WeightsParser {
    private static final String[] FIELDS = {"scales", "weights", "tieBreak", "stickiness", "holdTicks",
            "hpDropRelease"};

    private WeightsParser() {
    }

    static ScoreWeights parse(ConfigNode root) {
        root.fields(FIELDS);
        FactorScales scales = FactorScalesParser.parse(root.get("scales"));
        Map<Factor, Map<TacticalOption, Double>> matrix = matrix(root.get("weights"));
        return root.build(() -> new ScoreWeights(matrix, tieBreak(root.get("tieBreak")),
                root.get("stickiness").number(),
                root.get("holdTicks").integer(),
                root.get("hpDropRelease").number(),
                scales));
    }

    private static Map<Factor, Map<TacticalOption, Double>> matrix(ConfigNode weights) {
        Map<String, ConfigNode> rows = weights.entries();
        Map<Factor, Map<TacticalOption, Double>> matrix = new EnumMap<>(Factor.class);
        for (Map.Entry<String, ConfigNode> row : rows.entrySet()) {
            Factor factor = row.getValue().enumValueOf(Factor.class, row.getKey(), "фактор");
            if (matrix.put(factor, rowOf(row.getValue())) != null) {
                throw row.getValue().fail("фактор " + factor + " описан дважды", "ключи регистронезависимы: уберите дубль");
            }
        }
        for (Factor factor : Factor.values()) {
            if (!matrix.containsKey(factor)) {
                throw weights.fail("нет строки весов для фактора " + factor,
                        "матрица весов полная: у каждого фактора есть вес для FIGHT, RETREAT и IGNORE");
            }
        }
        return matrix;
    }

    private static Map<TacticalOption, Double> rowOf(ConfigNode row) {
        Map<TacticalOption, Double> weights = new EnumMap<>(TacticalOption.class);
        for (Map.Entry<String, ConfigNode> cell : row.entries().entrySet()) {
            TacticalOption option = cell.getValue().enumValueOf(TacticalOption.class, cell.getKey(), "вариант решения");
            if (weights.put(option, cell.getValue().number(-ScoreWeights.MAX_WEIGHT, ScoreWeights.MAX_WEIGHT)) != null) {
                throw cell.getValue().fail("вариант " + option + " описан дважды", "ключи регистронезависимы: уберите дубль");
            }
        }
        for (TacticalOption option : TacticalOption.values()) {
            if (!weights.containsKey(option)) {
                throw row.fail("нет веса для варианта " + option,
                        "каждая строка содержит веса для FIGHT, RETREAT и IGNORE");
            }
        }
        return weights;
    }

    private static List<TacticalOption> tieBreak(ConfigNode node) {
        List<TacticalOption> order = new ArrayList<>();
        Set<TacticalOption> seen = new HashSet<>();
        for (ConfigNode item : node.nonEmptyItems()) {
            TacticalOption option = item.enumValue(TacticalOption.class);
            if (!seen.add(option)) {
                throw item.fail("вариант " + option + " повторён", "порядок ничьей называет каждый вариант один раз");
            }
            order.add(option);
        }
        if (order.size() != TacticalOption.values().length) {
            throw node.fail("в порядке ничьей " + order.size() + " вариантов из " + TacticalOption.values().length,
                    "перечислите FIGHT, RETREAT и IGNORE: иначе ничья не разрешится для пропущенного");
        }
        return order;
    }
}

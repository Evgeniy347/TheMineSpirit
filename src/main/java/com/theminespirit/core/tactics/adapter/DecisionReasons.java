package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.Factor;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.Trace;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class DecisionReasons {
    public static final int TOP_FACTORS = 3;

    private DecisionReasons() {
    }

    public static List<ReasonFactor> topFactors(Trace trace, TacticalOption chosen) {
        Map<TacticalOption, Double> totals = trace.totals();
        TacticalOption rival = rivalOf(totals, chosen);
        List<ReasonFactor> factors = new ArrayList<>();
        for (Trace.FactorShare share : trace.factors()) {
            double weight = share(chosen, share) - share(rival, share);
            if (weight > 0) {
                factors.add(new ReasonFactor(share.factor().name(), share.value(), weight));
            }
        }
        factors.sort(Comparator.comparingDouble(ReasonFactor::weight).reversed()
                .thenComparingInt(factor -> Factor.valueOf(factor.factor()).ordinal()));
        return List.copyOf(factors.subList(0, Math.min(TOP_FACTORS, factors.size())));
    }

    private static TacticalOption rivalOf(Map<TacticalOption, Double> totals, TacticalOption chosen) {
        return totals.entrySet().stream().filter(entry -> entry.getKey() != chosen)
                .max(Comparator.<Map.Entry<TacticalOption, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparingInt(entry -> entry.getKey().ordinal()))
                .map(Map.Entry::getKey).orElse(chosen);
    }

    private static double share(TacticalOption option, Trace.FactorShare factor) {
        return switch (option) {
            case FIGHT -> factor.fight();
            case RETREAT -> factor.retreat();
            case IGNORE -> factor.ignore();
        };
    }
}

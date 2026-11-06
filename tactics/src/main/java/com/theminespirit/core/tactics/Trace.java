package com.theminespirit.core.tactics;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record Trace(
        String rule,
        HoldStatus hold,
        List<String> excludingRules,
        Map<TacticalOption, Double> totals,
        List<FactorShare> factors,
        String actionRule,
        Optional<String> retreatRule,
        Optional<String> stateRule,
        Optional<String> modeRule,
        String whyNotFight) {
    public Trace {
        excludingRules = List.copyOf(excludingRules);
        totals = Map.copyOf(totals);
        factors = List.copyOf(factors);
    }

    public String explain() {
        StringBuilder text = new StringBuilder("решение по правилу ")
                .append(rule)
                .append(", удержание ")
                .append(hold)
                .append(", счёт ")
                .append(totals)
                .append(", действие по правилу ")
                .append(actionRule);
        retreatRule.ifPresent(id -> text.append(", отход по правилу ").append(id));
        stateRule.ifPresent(id -> text.append(", состояние по правилу ").append(id));
        modeRule.ifPresent(id -> text.append(", режим по правилу ").append(id));
        if (!whyNotFight.isEmpty()) {
            text.append("; почему не бой: ").append(whyNotFight);
        }
        return text.toString();
    }

    public record FactorShare(Factor factor, double value, double fight, double retreat, double ignore) {}
}

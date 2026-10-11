package com.theminespirit.core.tactics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class HardRules {
    private final List<HardRule> rules;
    private final RuleCounter counter;

    public HardRules(List<HardRule> rules, RuleCounter counter) {
        this.rules = List.copyOf(rules);
        this.counter = counter;
    }

    public HardVerdict evaluate(FactMap facts) {
        Set<TacticalOption> excluded = EnumSet.noneOf(TacticalOption.class);
        List<String> excluding = new ArrayList<>();
        for (HardRule rule : rules) {
            if (rule.noRouteOption().isPresent()) {
                continue;
            }
            counter.check();
            if (!rule.when().matches(facts)) {
                continue;
            }
            if (rule.decision().isPresent()) {
                return new HardVerdict(Optional.of(rule), excluded, excluding);
            }
            excluded.addAll(rule.excludes());
            excluding.add(rule.id());
        }
        return new HardVerdict(Optional.empty(), excluded, excluding);
    }

    public Optional<HardRule> noRouteReplacement(FactMap facts) {
        Set<TacticalOption> excluded = EnumSet.noneOf(TacticalOption.class);
        for (HardRule rule : rules) {
            if (!rule.excludes().isEmpty()) {
                counter.check();
                if (rule.when().matches(facts)) {
                    excluded.addAll(rule.excludes());
                }
            }
        }
        for (HardRule rule : rules) {
            if (rule.noRouteOption().isPresent() && !excluded.contains(rule.noRouteOption().get())) {
                counter.check();
                if (rule.when().matches(facts)) {
                    return Optional.of(rule);
                }
            }
        }
        return Optional.empty();
    }
}

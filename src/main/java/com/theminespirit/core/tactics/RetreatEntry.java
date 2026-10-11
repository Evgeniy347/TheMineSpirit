package com.theminespirit.core.tactics;

import java.util.List;
import java.util.Optional;

public record RetreatEntry(String id, RetreatMethod method, Condition requires, double base, Optional<FactName> fact,
        double perUnit, List<Bonus> bonuses) {
    public RetreatEntry {
        bonuses = List.copyOf(bonuses);
    }

    public double utility(FactMap facts) {
        double value = base + fact.map(name -> perUnit * facts.number(name)).orElse(0.0);
        for (Bonus bonus : bonuses) {
            if (bonus.when().matches(facts)) {
                value += bonus.add();
            }
        }
        return WinMargin.clamp(value, 0, 1);
    }

    public record Bonus(Condition when, double add) {
    }
}

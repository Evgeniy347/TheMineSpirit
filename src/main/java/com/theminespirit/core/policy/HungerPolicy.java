package com.theminespirit.core.policy;

import com.theminespirit.core.hunger.HungerRules;
import java.util.Objects;

public final class HungerPolicy {
    private HungerPolicy() {
    }

    public static PolicyVerdict evaluate(int foodLevel, HungerRules rules, boolean hasFood, boolean inCombat) {
        Objects.requireNonNull(rules, "Правила голода не могут быть null: порог обеда — данные, а не константа кода");
        if (inCombat) {
            return new PolicyVerdict.Clear();
        }
        if (foodLevel > rules.eatFoodThreshold()) {
            return new PolicyVerdict.Clear();
        }
        if (!hasFood) {
            return new PolicyVerdict.Clear();
        }
        return new PolicyVerdict.Reflex(PolicyId.HUNGER, new ReflexAction.Eat(),
                "сытость " + foodLevel + " не выше порога " + rules.eatFoodThreshold() + ": дух ест");
    }
}

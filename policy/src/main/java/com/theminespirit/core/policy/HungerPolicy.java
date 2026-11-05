package com.theminespirit.core.policy;

public final class HungerPolicy {
    private HungerPolicy() {}

    public static PolicyVerdict evaluate(int foodLevel, int eatFoodThreshold, boolean hasFood, boolean inCombat) {
        if (inCombat) {
            return new PolicyVerdict.Clear();
        }
        if (foodLevel > eatFoodThreshold) {
            return new PolicyVerdict.Clear();
        }
        if (!hasFood) {
            return new PolicyVerdict.Clear();
        }
        return new PolicyVerdict.Reflex(
                PolicyId.HUNGER,
                new ReflexAction.Eat(),
                "сытость " + foodLevel + " не выше порога " + eatFoodThreshold + ": дух ест");
    }
}

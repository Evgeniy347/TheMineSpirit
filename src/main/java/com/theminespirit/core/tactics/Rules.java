package com.theminespirit.core.tactics;

import java.util.Comparator;
import java.util.List;

public record Rules(
        List<HardRule> hard,
        List<ActionRule> actions,
        List<ModeTransition> modeTransitions,
        List<StateTransition> states,
        RetreatTable retreat) {
    public Rules {
        hard = byPriority(hard, Comparator.comparingInt(HardRule::priority).reversed());
        actions = byPriority(
                actions, Comparator.comparingInt(ActionRule::priority).reversed());
        modeTransitions = List.copyOf(modeTransitions);
        states = List.copyOf(states);
    }

    private static <T> List<T> byPriority(List<T> rules, Comparator<T> order) {
        return rules.stream().sorted(order).toList();
    }
}

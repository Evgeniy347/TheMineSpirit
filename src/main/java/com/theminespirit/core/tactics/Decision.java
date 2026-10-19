package com.theminespirit.core.tactics;

import java.util.List;
import java.util.Set;

public record Decision(
        TacticalOption option,
        String rule,
        Set<TacticalOption> excluded,
        List<String> excludingRules,
        HoldStatus hold) {
    public static final String BY_SCORE = "score";
    public static final String SOLE_CANDIDATE = "sole_candidate";
    public static final String ALL_EXCLUDED = "all_excluded";
    public static final String HELD = "hold";

    public Decision {
        excluded = Set.copyOf(excluded);
        excludingRules = List.copyOf(excludingRules);
    }

    public DecisionSource source() {
        return BY_SCORE.equals(rule) || HELD.equals(rule) ? DecisionSource.SCORE : DecisionSource.RULE;
    }
}

package com.theminespirit.core.tactics;

import java.util.List;
import java.util.Set;

public record Decision(TacticalOption option, String rule, Set<TacticalOption> excluded, List<String> excludingRules) {
    public static final String BY_SCORE = "score";
    public static final String SOLE_CANDIDATE = "sole_candidate";
    public static final String ALL_EXCLUDED = "all_excluded";

    public Decision {
        excluded = Set.copyOf(excluded);
        excludingRules = List.copyOf(excludingRules);
    }
}

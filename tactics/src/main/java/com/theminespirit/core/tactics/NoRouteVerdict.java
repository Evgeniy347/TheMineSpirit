package com.theminespirit.core.tactics;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record NoRouteVerdict(Optional<HardRule> rule, Set<TacticalOption> excluded, List<String> excludingRules) {
    public NoRouteVerdict {
        excluded = Set.copyOf(excluded);
        excludingRules = List.copyOf(excludingRules);
    }
}

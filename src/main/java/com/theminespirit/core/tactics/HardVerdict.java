package com.theminespirit.core.tactics;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record HardVerdict(Optional<HardRule> decisive, Set<TacticalOption> excluded, List<String> excludingRules) {
    public HardVerdict {
        excluded = Set.copyOf(excluded);
        excludingRules = List.copyOf(excludingRules);
    }
}

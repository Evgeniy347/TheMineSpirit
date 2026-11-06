package com.theminespirit.core.tactics;

import java.util.Map;

public record EnemyProfiles(Map<String, EnemyProfile> byType, EnemyProfile fallback) {
    public static final String DEFAULT_KEY = "default";

    public EnemyProfiles {
        byType = Map.copyOf(byType);
    }

    public EnemyProfile profile(String type) {
        EnemyProfile known = byType.get(type);
        return known != null ? known : fallback;
    }

    public boolean known(String type) {
        return byType.containsKey(type);
    }
}

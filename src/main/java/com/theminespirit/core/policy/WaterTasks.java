package com.theminespirit.core.policy;

import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class WaterTasks {
    private static final Set<String> SWIMMING = Set.of("swim", "cross_water", "fish");

    private WaterTasks() {}

    public static boolean allowsSwimming(@Nullable String script) {
        return script != null && SWIMMING.contains(script);
    }
}

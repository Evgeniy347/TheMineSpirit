package com.theminespirit.core.board.graph;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class DigFacts {
    public static final String DIG_OUT_ITEM = "dig_open";
    public static final String BRIDGE_ITEM = "bridge_open";
    public static final String PILLAR_ITEM = "pillar_open";
    private static final String PIT = "pit";
    private static final String WATER = "water";

    public enum Cause {
        OPEN,
        SEALED,
        PIT,
        WATER,
        WALL
    }

    private DigFacts() {}

    public static @Nullable String trap(Cause cause) {
        return cause == Cause.OPEN ? null : cause.name().toLowerCase(Locale.ROOT);
    }

    public static String node(@Nullable String cause) {
        if (PIT.equals(cause)) {
            return PILLAR_ITEM;
        }
        if (WATER.equals(cause)) {
            return BRIDGE_ITEM;
        }
        return DIG_OUT_ITEM;
    }
}

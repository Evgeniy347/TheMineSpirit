package com.theminespirit.core.policy;

import java.util.Objects;
import java.util.UUID;

public final class ReflexSchedule {
    public static final int PERIOD_TICKS = 10;

    private ReflexSchedule() {}

    public static int offsetOf(UUID spirit) {
        Objects.requireNonNull(spirit, "UUID духа не может быть null: по нему расписание разносит духов по тикам");
        return Math.floorMod(spirit.hashCode(), PERIOD_TICKS);
    }

    public static boolean tierOneDue(long gameTick, int offset, boolean blockChanged, boolean escalated) {
        return blockChanged || escalated || onPhase(gameTick, offset);
    }

    public static boolean tierTwoDue(long gameTick, int offset) {
        return onPhase(gameTick, offset);
    }

    private static boolean onPhase(long gameTick, int offset) {
        return Math.floorMod(gameTick + offset, PERIOD_TICKS) == 0;
    }
}

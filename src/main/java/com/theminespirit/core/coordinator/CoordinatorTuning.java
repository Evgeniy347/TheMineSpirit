package com.theminespirit.core.coordinator;

public final class CoordinatorTuning {
    public static final long SNAPSHOT_INTERVAL_TICKS = 10;
    public static final double DEFENSE_RADIUS = 16;
    public static final double DEFENSE_POST_RADIUS = 3;
    public static final long WORK_LEASE_TICKS = 600;
    public static final int MAX_ASSIST = 2;
    public static final long SNAPSHOT_STALE_TICKS = 40;
    public static final int SNAPSHOT_GROUP_QUERIES = 1;
    public static final int SNAPSHOT_SPIRIT_QUERIES = 1;
    public static final long CALM_POST_IDLE_TICKS = 200;

    private CoordinatorTuning() {}
}

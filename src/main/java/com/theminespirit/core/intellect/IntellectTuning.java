package com.theminespirit.core.intellect;

public final class IntellectTuning {
    public static final long DEMAND_INTERVAL_TICKS = 100;
    public static final long MIN_SWITCH_TICKS = 200;
    public static final long FAIL_BACKOFF_TICKS = 200;
    public static final double HYSTERESIS_MARGIN = 0.15;
    public static final double CROWD_PENALTY = 0.2;
    public static final double MAX_DEMAND = 10.0;
    public static final double FLEE_MARGIN = 1.0;
    public static final int MAX_FIGHTERS = 3;
    public static final double FOOD_DAYS = 1;
    public static final double FOOD_RATION = 1.5;
    public static final double FOOD_CHILD_RATION = 0.75;
    public static final double FOOD_DEMAND_MAX = 9.0;
    public static final int FORAGE_WITHDRAW_COUNT = 1;
    public static final int FORAGE_WITHDRAW_RADIUS = 8;
    public static final int FORAGE_HUNT_RADIUS = 16;
    public static final int FORAGE_HUNT_COUNT = 1;
    public static final int FORAGE_FISH_RADIUS = 16;
    public static final int FORAGE_FISH_COUNT = 1;
    public static final int FORAGE_FISH_DURATION = 1200;
    public static final int ORE_TARGET = 64;
    public static final int IRON_TARGET = 32;
    public static final int WEAPON_TARGET = 2;
    public static final int TEAM_MIN = 2;
    public static final int TEAM_MAX = 3;
    public static final long INVITE_TTL_TICKS = 3600;
    public static final long INVITE_REPEAT_TICKS = 600;
    public static final long INVITE_EXTEND_TICKS = 1200;
    public static final long STRATEGIC_TTL_TICKS = 6000;
    public static final double HEAL_FRACTION = 0.35;
    public static final double ARMED_DPS = 6.0;

    private IntellectTuning() {
    }
}

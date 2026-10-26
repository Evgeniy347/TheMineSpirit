package com.theminespirit.core.script;

import com.theminespirit.core.observation.ScanRadius;

public final class CatalogLimits {
    public static final int COUNT_MIN = 1;
    public static final int COUNT_MAX = 256;

    public static final int DROP_RADIUS_MIN = 1;
    public static final int DROP_RADIUS_MAX = 16;
    public static final int DROP_RADIUS_DEFAULT = 8;

    public static final int REPEAT_TIMES_MIN = 1;
    public static final int REPEAT_TIMES_MAX = 64;

    public static final int STATION_RADIUS_MIN = 1;
    public static final int STATION_RADIUS_MAX = 32;
    public static final int STATION_RADIUS_DEFAULT = 16;

    public static final int WAIT_TICKS_MIN = 1;
    public static final int WAIT_TICKS_MAX = 6000;

    public static final int CONTAINER_RADIUS_MIN = 1;
    public static final int CONTAINER_RADIUS_MAX = 16;
    public static final int CONTAINER_RADIUS_DEFAULT = 8;

    public static final int UNLOAD_TRIGGER_SLOTS = 24;

    public static final int FOLLOW_RADIUS_MIN = 0;
    public static final int FOLLOW_RADIUS_MAX = 16;
    public static final int FOLLOW_RADIUS_DEFAULT = 3;

    public static final int ESCORT_UNTIL_MIN = 0;
    public static final int ESCORT_UNTIL_MAX = 6000;
    public static final int ESCORT_UNTIL_DEFAULT = 0;

    public static final int EXPLORE_BUDGET_MIN = 1;
    public static final int EXPLORE_BUDGET_MAX = 64;
    public static final int EXPLORE_BUDGET_DEFAULT = 16;

    public static final int CHOP_RADIUS_MIN = 1;
    public static final int CHOP_RADIUS_MAX = 100;

    public static final int WORLD_Y_MIN = -64;
    public static final int WORLD_Y_MAX = 319;
    public static final int WORLD_HORIZONTAL_LIMIT = 30_000_000;

    public static final int HARVEST_RADIUS_MIN = 1;
    public static final int HARVEST_RADIUS_MAX = 32;
    public static final int HARVEST_RADIUS_DEFAULT = 16;

    public static final int HARVEST_NO_PROGRESS_TICKS = 1200;

    public static final int FARM_RADIUS_MIN = 1;
    public static final int FARM_RADIUS_MAX = 16;
    public static final int FARM_RADIUS_DEFAULT = 8;

    public static final int TILL_COUNT_DEFAULT = 16;
    public static final int PLANT_COUNT_DEFAULT = 64;
    public static final int BONE_MEAL_COUNT_DEFAULT = 16;

    public static final int FARM_PLOT_SIDE_MAX = 16;

    public static final int FENCE_CORNERS_MIN = 3;
    public static final int FENCE_CORNERS_MAX = 64;
    public static final int FENCE_BLOCKS_MAX = 512;

    public static final int NAV_RADIUS_MIN = 1;
    public static final int NAV_RADIUS_MAX = 32;
    public static final int NAV_RADIUS_DEFAULT = 16;

    public static final int NAV_PATROL_POINTS_MAX = 16;

    public static final int NAV_PATROL_LOOPS_MIN = 1;
    public static final int NAV_PATROL_LOOPS_MAX = 16;
    public static final int NAV_PATROL_LOOPS_DEFAULT = 1;
    public static final String NAV_PATROL_MODE_DEFAULT = "loop";

    public static final int NAV_WANDER_DURATION_MIN = 20;
    public static final int NAV_WANDER_DURATION_MAX = 6000;
    public static final int NAV_WANDER_DURATION_DEFAULT = 600;

    public static final int NAV_KEEP_DISTANCE_MIN = 1;
    public static final int NAV_KEEP_DISTANCE_MAX = 16;
    public static final int NAV_KEEP_DISTANCE_DEFAULT = 5;

    public static final double NAV_ARRIVAL_TOLERANCE = 1.25;

    public static final int PERCEPTION_RADIUS_MIN = ScanRadius.MIN;
    public static final int PERCEPTION_RADIUS_MAX = ScanRadius.MAX;
    public static final int PERCEPTION_RADIUS_DEFAULT = ScanRadius.DEFAULT.value();

    public static final int HERD_PEN_RADIUS_MIN = 2;
    public static final int HERD_PEN_RADIUS_MAX = 16;

    public static final int HERD_RADIUS_MIN = 1;
    public static final int HERD_RADIUS_MAX = 16;
    public static final int HERD_RADIUS_DEFAULT = 8;

    public static final int HERD_HUNT_RADIUS_MIN = 1;
    public static final int HERD_HUNT_RADIUS_MAX = 48;
    public static final int HERD_HUNT_RADIUS_DEFAULT = 16;

    public static final int HERD_PAIRS_MIN = 1;
    public static final int HERD_PAIRS_MAX = 8;
    public static final int HERD_PAIRS_DEFAULT = 1;

    public static final int HERD_COUNT_MIN = 1;
    public static final int HERD_COUNT_MAX = 64;
    public static final int HERD_FEED_COUNT_DEFAULT = 8;
    public static final int HERD_WORK_COUNT_DEFAULT = 64;

    public static final int HIVE_COUNT_DEFAULT = 1;

    public static final int HERD_KEEP_MIN = 2;
    public static final int HERD_KEEP_MAX = 64;
    public static final int HERD_KEEP_DEFAULT_CATTLE = 4;
    public static final int HERD_KEEP_DEFAULT_CHICKEN = 8;

    public static final int HERD_REFRESH_TICKS = 20;

    public static final int HERD_HOME_RADIUS = 8;

    public static final int CAPTURE_RADIUS_MIN = 1;
    public static final int CAPTURE_RADIUS_MAX = 16;
    public static final int CAPTURE_RADIUS_DEFAULT = 8;
    public static final int CAPTURE_LEAD_TICKS = 1200;
    public static final int CAPTURE_PEN_RADIUS = 3;
    public static final int HERD_SIGHT_MARGIN = 4;

    public static final int ACQUIRE_MAX_ATTEMPTS = 3;

    public static final int DIG_SIZE_MIN = 1;
    public static final int DIG_SIZE_MAX = 16;

    public static final int MINE_LENGTH_MIN = 1;
    public static final int MINE_LENGTH_MAX = 256;
    public static final int MINE_LENGTH_DEFAULT = 16;
    public static final int BUILD_VOLUME_MAX = 4096;
    public static final int SCHEMATIC_SIZE_MAX = 32;
    public static final int UNDERGROUND_REPEAT_SIZE_MAX = 160;
    public static final int SCHEMATIC_BLOCKS_MAX = 4096;

    public static final int LIGHT_RADIUS_MIN = 1;
    public static final int LIGHT_RADIUS_MAX = 32;
    public static final int LIGHT_RADIUS_DEFAULT = 16;

    public static final int BRIDGE_LENGTH_MIN = 1;
    public static final int BRIDGE_LENGTH_MAX = 64;

    public static final int CAMP_RADIUS_MIN = 1;
    public static final int CAMP_RADIUS_MAX = 32;
    public static final int CAMP_RADIUS_DEFAULT = 16;

    public static final int FISH_RADIUS_MIN = 1;
    public static final int FISH_RADIUS_MAX = 32;
    public static final int FISH_RADIUS_DEFAULT = 16;
    public static final int FISH_COUNT_DEFAULT = 1;
    public static final int FISH_DURATION_MIN = 20;
    public static final int FISH_DURATION_MAX = WAIT_TICKS_MAX;
    public static final int FISH_DURATION_DEFAULT = 1200;

    public static final int SAY_LENGTH_MAX = 256;
    public static final int SAY_MIN_INTERVAL_TICKS = 20;
    public static final int SAY_HEARING_RADIUS = 64;
    public static final int ATTACK_TIMEOUT_TICKS = 1200;
    public static final int DEFEND_TIMEOUT_TICKS = 1200;
    public static final int DEFEND_SAFE_TICKS = 60;
    public static final int FLEE_TIMEOUT_TICKS = 600;
    public static final int FLEE_SAFE_TICKS = 40;
    public static final int RETREAT_TIMEOUT_TICKS = 1200;

    public static final int CLIMB_TIMEOUT_TICKS = 1200;

    public static final int DISENGAGE_TIMEOUT_TICKS = 600;
    public static final int DISENGAGE_DISTANCE_MIN = 1;
    public static final int DISENGAGE_DISTANCE_MAX = 16;
    public static final int DISENGAGE_DISTANCE_DEFAULT = 5;

    private CatalogLimits() {}
}

package com.theminespirit.core.script;

import com.theminespirit.core.observation.ScanRadius;

public final class CatalogLimits {
    private CatalogLimits() {
    }

    // docs/API.md: count 1..256
    public static final int COUNT_MIN = 1;
    public static final int COUNT_MAX = 256;

    public static final int SCAN_RADIUS_MIN = ScanRadius.MIN;
    public static final int SCAN_RADIUS_MAX = ScanRadius.MAX;

    public static final int DROP_RADIUS_MIN = 1;
    public static final int DROP_RADIUS_MAX = 16;
    public static final int DROP_RADIUS_DEFAULT = 8;

    // AGENTS.md: лимиты плана (repeat.times 1..64)
    public static final int REPEAT_TIMES_MIN = 1;
    public static final int REPEAT_TIMES_MAX = 64;

    // AGENTS.md: wait.ticks 1..6000 (5 минут)
    public static final int WAIT_TICKS_MIN = 1;
    public static final int WAIT_TICKS_MAX = 6000;

    // Радиус поиска ближайшего контейнера у скриптов инвентаря
    public static final int CONTAINER_RADIUS_MIN = 1;
    public static final int CONTAINER_RADIUS_MAX = 16;
    public static final int CONTAINER_RADIUS_DEFAULT = 8;

    // Сколько слотов инвентаря занимает дух, прежде чем разгружаться на базе
    public static final int UNLOAD_TRIGGER_SLOTS = 24;

    // AGENTS.md: follow.radius 0..16
    public static final int FOLLOW_RADIUS_MIN = 0;
    public static final int FOLLOW_RADIUS_MAX = 16;

    public static final int CHOP_RADIUS_MIN = 1;
    public static final int CHOP_RADIUS_MAX = 32;

    // AGENTS.md/карта мира
    public static final int WORLD_Y_MIN = -64;
    public static final int WORLD_Y_MAX = 319;
    public static final int WORLD_HORIZONTAL_LIMIT = 30_000_000;

    public static final int HARVEST_RADIUS_MIN = 1;
    public static final int HARVEST_RADIUS_MAX = 32;
    public static final int HARVEST_RADIUS_DEFAULT = 16;

    public static final int FARM_RADIUS_MIN = 1;
    public static final int FARM_RADIUS_MAX = 16;
    public static final int FARM_RADIUS_DEFAULT = 8;

    public static final int TILL_COUNT_DEFAULT = 16;
    public static final int PLANT_COUNT_DEFAULT = 64;
    public static final int BONE_MEAL_COUNT_DEFAULT = 16;
}

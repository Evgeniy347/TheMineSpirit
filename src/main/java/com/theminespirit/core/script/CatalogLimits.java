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

    // Радиус поиска станции (верстак, печь) у скриптов craft и smelt
    public static final int STATION_RADIUS_MIN = 1;
    public static final int STATION_RADIUS_MAX = 32;
    public static final int STATION_RADIUS_DEFAULT = 16;

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

    // Загон стада: радиус загона 2..16, поиск скриптов стада 1..16
    public static final int HERD_PEN_RADIUS_MIN = 2;
    public static final int HERD_PEN_RADIUS_MAX = 16;

    // Скрипты стада: радиус поиска, пары разведения, число голов за запуск
    public static final int HERD_RADIUS_MIN = 1;
    public static final int HERD_RADIUS_MAX = 16;
    public static final int HERD_RADIUS_DEFAULT = 8;

    public static final int HERD_HUNT_RADIUS_MIN = 1;
    public static final int HERD_HUNT_RADIUS_MAX = 32;
    public static final int HERD_HUNT_RADIUS_DEFAULT = 16;

    public static final int HERD_PAIRS_MIN = 1;
    public static final int HERD_PAIRS_MAX = 8;
    public static final int HERD_PAIRS_DEFAULT = 1;

    public static final int HERD_COUNT_MIN = 1;
    public static final int HERD_COUNT_MAX = 64;
    public static final int HERD_FEED_COUNT_DEFAULT = 8;
    public static final int HERD_WORK_COUNT_DEFAULT = 64;

    // Норма поголовья: минимум пара для разведения, курам норма выше
    public static final int HERD_KEEP_MIN = 2;
    public static final int HERD_KEEP_MAX = 64;
    public static final int HERD_KEEP_DEFAULT_CATTLE = 4;
    public static final int HERD_KEEP_DEFAULT_CHICKEN = 8;

    // Снимок стада обновляется раз в 20 тиков, между обновлениями — кэш
    public static final int HERD_REFRESH_TICKS = 20;

    // Охота не трогает животных в домашнем круге старта духа
    public static final int HERD_HOME_RADIUS = 8;

    // Дух замечает отбившихся чуть дальше края загона
    public static final int HERD_SIGHT_MARGIN = 4;
}

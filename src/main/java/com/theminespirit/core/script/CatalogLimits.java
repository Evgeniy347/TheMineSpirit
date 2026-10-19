package com.theminespirit.core.script;

import com.theminespirit.core.observation.ScanRadius;

public final class CatalogLimits {
    private CatalogLimits() {
    }

    // docs/API.md: count 1..256
    public static final int COUNT_MIN = 1;
    public static final int COUNT_MAX = 256;

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

    // AGENTS.md: follow.radius 0..16; escort держится рядом в тех же пределах
    public static final int FOLLOW_RADIUS_MIN = 0;
    public static final int FOLLOW_RADIUS_MAX = 16;
    public static final int FOLLOW_RADIUS_DEFAULT = 3;

    // escort.until: 0 — до команды стоп, больше нуля — предел в тиках (как wait.ticks)
    public static final int ESCORT_UNTIL_MIN = 0;
    public static final int ESCORT_UNTIL_MAX = 6000;
    public static final int ESCORT_UNTIL_DEFAULT = 0;

    // explore.budget: сколько точек обхода разрешено за запуск
    public static final int EXPLORE_BUDGET_MIN = 1;
    public static final int EXPLORE_BUDGET_MAX = 64;
    public static final int EXPLORE_BUDGET_DEFAULT = 16;

    public static final int CHOP_RADIUS_MIN = 1;
    public static final int CHOP_RADIUS_MAX = 32;

    // AGENTS.md/карта мира
    public static final int WORLD_Y_MIN = -64;
    public static final int WORLD_Y_MAX = 319;
    public static final int WORLD_HORIZONTAL_LIMIT = 30_000_000;

    public static final int HARVEST_RADIUS_MIN = 1;
    public static final int HARVEST_RADIUS_MAX = 32;
    public static final int HARVEST_RADIUS_DEFAULT = 16;

    // Добыча без единого добытого блока столько тиков падает с причиной, а не висит:
    // пять минут с запасом перекрывают самый долгий подход (радиус 32 ≈ 2400 тиков).
    public static final int HARVEST_NO_PROGRESS_TICKS = 6000;

    public static final int FARM_RADIUS_MIN = 1;
    public static final int FARM_RADIUS_MAX = 16;
    public static final int FARM_RADIUS_DEFAULT = 8;

    public static final int TILL_COUNT_DEFAULT = 16;
    public static final int PLANT_COUNT_DEFAULT = 64;
    public static final int BONE_MEAL_COUNT_DEFAULT = 16;

    // build_farm: прямоугольная грядка вокруг духа
    public static final int FARM_PLOT_SIDE_MIN = 1;
    public static final int FARM_PLOT_SIDE_MAX = 16;

    // build_shelter: сторона укрытия и предел блоков, как бюджет укрепления
    public static final int SHELTER_SIDE_MIN = 3;
    public static final int SHELTER_SIDE_MAX = 4;
    public static final int SHELTER_BLOCKS_MAX = 64;

    // build_fence: точки контура и предел клеток забора
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

    // Загон стада: радиус загона 2..16, поиск скриптов стада 1..16
    public static final int HERD_PEN_RADIUS_MIN = 2;
    public static final int HERD_PEN_RADIUS_MAX = 16;

    // Скрипты стада: радиус поиска, пары разведения, число голов за запуск
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

    // Норма поголовья: минимум пара для разведения, курам норма выше
    public static final int HERD_KEEP_MIN = 2;
    public static final int HERD_KEEP_MAX = 64;
    public static final int HERD_KEEP_DEFAULT_CATTLE = 4;
    public static final int HERD_KEEP_DEFAULT_CHICKEN = 8;

    // Снимок стада обновляется раз в 20 тиков, между обновлениями — кэш
    public static final int HERD_REFRESH_TICKS = 20;

    // Охота не трогает животных в домашнем круге старта духа
    public static final int HERD_HOME_RADIUS = 8;

    // Отлов кольцом: радиус поиска животного, предел наращивания слабых мест
    public static final int CAPTURE_RADIUS_MIN = 1;
    public static final int CAPTURE_RADIUS_MAX = 16;
    public static final int CAPTURE_RADIUS_DEFAULT = 8;
    public static final int CAPTURE_LEAD_TICKS = 1200;
    public static final int CAPTURE_PEN_RADIUS = 3;
    // Дух замечает отбившихся чуть дальше края загона
    public static final int HERD_SIGHT_MARGIN = 4;

    // acquire: сколько раз подплан разрешается пересобрать после провала шага
    public static final int ACQUIRE_MAX_ATTEMPTS = 3;

    public static final int DIG_SIZE_MIN = 1;
    public static final int DIG_SIZE_MAX = 16;

    // Безопасная шахта (safe_mine): длина штольни в блоках
    public static final int MINE_LENGTH_MIN = 1;
    public static final int MINE_LENGTH_MAX = 256;
    public static final int MINE_LENGTH_DEFAULT = 16;
    public static final int BUILD_VOLUME_MAX = 4096;
    public static final int SCHEMATIC_SIZE_MAX = 32;
    public static final int SCHEMATIC_BLOCKS_MAX = 4096;
    public static final int BLOCKS_PER_TICK = 1;

    // Освещение территории (light_area): радиус квадрата вокруг точки
    public static final int LIGHT_RADIUS_MIN = 1;
    public static final int LIGHT_RADIUS_MAX = 32;
    public static final int LIGHT_RADIUS_DEFAULT = 16;

    // Мост через провал или воду: сколько блоков ставится подряд (bridge.length)
    public static final int BRIDGE_LENGTH_MIN = 1;
    public static final int BRIDGE_LENGTH_MAX = 64;

    // Лагерь похода: радиус спокойной зоны скриптов set_camp/clear_camp
    public static final int CAMP_RADIUS_MIN = 1;
    public static final int CAMP_RADIUS_MAX = 32;
    public static final int CAMP_RADIUS_DEFAULT = 16;

    // Рыбалка: радиус поиска открытой воды, число рыбы за запуск и время ловли
    public static final int FISH_RADIUS_MIN = 1;
    public static final int FISH_RADIUS_MAX = 32;
    public static final int FISH_RADIUS_DEFAULT = 16;
    public static final int FISH_COUNT_DEFAULT = 1;
    public static final int FISH_DURATION_MIN = 20;
    public static final int FISH_DURATION_MAX = WAIT_TICKS_MAX;
    public static final int FISH_DURATION_DEFAULT = 1200;

    // Боевые и социальные скрипты: тайм-ауты и безопасные интервалы в тиках духа (20/сек)
    public static final int SAY_LENGTH_MAX = 256;
    public static final int SAY_MIN_INTERVAL_TICKS = 20;
    public static final int SAY_HEARING_RADIUS = 64;
    public static final int ATTACK_TIMEOUT_TICKS = 1200;
    public static final int DEFEND_TIMEOUT_TICKS = 1200;
    public static final int DEFEND_SAFE_TICKS = 60;
    public static final int FLEE_TIMEOUT_TICKS = 600;
    public static final int FLEE_SAFE_TICKS = 40;
    public static final int RETREAT_TIMEOUT_TICKS = 1200;
    public static final int MEET_TIMEOUT_TICKS = 1200;
    public static final int DELIVER_TIMEOUT_TICKS = 1200;

    // climb: подъём/спуск по лестнице, лиане или строительным лесам до конца колонны
    public static final int CLIMB_TIMEOUT_TICKS = 1200;

    // disengage: выход из боя отходом на дистанцию от ближайшего врага
    public static final int DISENGAGE_TIMEOUT_TICKS = 600;
    public static final int DISENGAGE_DISTANCE_MIN = 1;
    public static final int DISENGAGE_DISTANCE_MAX = 16;
    public static final int DISENGAGE_DISTANCE_DEFAULT = 5;
}

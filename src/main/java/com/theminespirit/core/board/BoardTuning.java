package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import java.util.Set;

/**
 * Числа доски: значения по умолчанию. Источник истины для работающего мода — JSON-таблица
 * через DataTables (горячая перезагрузка /reload); здесь живут только значения, которые
 * используются, когда таблицы нет, и проверки их осмысленности.
 */
public final class BoardTuning {
    /** Пересборка доски: раз в 100 тиков (5 с), образец DEMAND_INTERVAL_TICKS. */
    public static final long BOARD_INTERVAL_TICKS = 100;

    /** Активных заданий одного духа по умолчанию — одно. */
    public static final int MAX_ACTIVE_PER_SPIRIT = 1;

    /** Предел очереди поселения: при превышении вытесняются самые старые невыданные. */
    public static final int MAX_PENDING = 64;

    /** Воронка «за территорией» — дальше 64 блоков от любого дома группы. */
    public static final int CRATER_TERRITORY_RADIUS = 64;

    /** Опрос животных раз в 200 тиков (10 с) одним ближайшим свободным духом. */
    public static final long HERD_SCAN_INTERVAL_TICKS = 200;

    /** Радиус воронки крипера по умолчанию, если радиус взрыва неизвестен. */
    public static final int CRATER_BLOCKS_ESTIMATE_RADIUS = 3;

    /** Верхняя граница приоритета задания: приоритеты лежат в [0, MAX_PRIORITY]. */
    public static final int MAX_PRIORITY = 100;

    /** Сколько контейнеров склада читается за одну пересборку доски. */
    public static final int STOCK_READ_BUDGET = 8;

    /** Сколько блоков мира читает снимок запасов за одну пересборку (образец ветки 32). */
    public static final int STOCK_BLOCK_BUDGET = 4096;

    /** Проваленное задание не переиздаётся раньше этого срока: 200 тиков (10 с). */
    public static final long FAIL_BACKOFF_TICKS = 200;

    /** После провала пара дух+кейс отдыхает: 1200 тиков (1 мин), а не ходит по кругу каждую пересборку. */
    public static final long CASE_FAIL_PAUSE_TICKS = 1200;

    /** Столько провалов подряд снимают кейс с этого духа: дальше его берёт другой, а не тот же снова. */
    public static final int CASE_FAIL_LIMIT = 3;

    /** Обзор периметра на дыры в заборе, которые не видит BreakEvent, — раз в 1000 тиков. */
    public static final long FENCE_PATROL_INTERVAL = 1000;

    /** Радиус от дома/духа, в котором сломанный забор считается своим, а не природным. */
    public static final int FENCE_WATCH_RADIUS = 32;

    /** Проверка чаровальни раз в 500 тиков (25 с). */
    public static final long ENCHANT_CHECK_INTERVAL = 500;

    /** Книг меньше — на доску задание «дополнить полки». */
    public static final int ENCHANT_SHELVES_MIN = 4;

    /** Ключи интервалов таблицы board.json: неизвестный ключ — опечатка, падает с путём файла. */
    public static final Set<String> INTERVAL_KEYS = Set.of(
            "boardIntervalTicks",
            "herdScanIntervalTicks",
            "craterTerritoryRadius",
            "maxPending",
            "maxActivePerSpirit",
            "stockReadBudget",
            "stockBlockBudget",
            "failBackoffTicks",
            "caseFailPauseTicks",
            "caseFailLimit",
            "fencePatrolInterval",
            "enchantCheckInterval",
            "enchantShelvesMin");

    public static long boardIntervalTicks() {
        return interval("boardIntervalTicks", BOARD_INTERVAL_TICKS);
    }

    public static long herdScanIntervalTicks() {
        return interval("herdScanIntervalTicks", HERD_SCAN_INTERVAL_TICKS);
    }

    public static int craterTerritoryRadius() {
        return (int) interval("craterTerritoryRadius", CRATER_TERRITORY_RADIUS);
    }

    public static int maxPending() {
        return (int) interval("maxPending", MAX_PENDING);
    }

    public static int maxActivePerSpirit() {
        return (int) interval("maxActivePerSpirit", MAX_ACTIVE_PER_SPIRIT);
    }

    public static int stockReadBudget() {
        return (int) interval("stockReadBudget", STOCK_READ_BUDGET);
    }

    public static int stockBlockBudget() {
        return (int) interval("stockBlockBudget", STOCK_BLOCK_BUDGET);
    }

    public static long failBackoffTicks() {
        return interval("failBackoffTicks", FAIL_BACKOFF_TICKS);
    }

    public static long caseFailPauseTicks() {
        return interval("caseFailPauseTicks", CASE_FAIL_PAUSE_TICKS);
    }

    public static int caseFailLimit() {
        return (int) interval("caseFailLimit", CASE_FAIL_LIMIT);
    }

    public static long fencePatrolInterval() {
        return interval("fencePatrolInterval", FENCE_PATROL_INTERVAL);
    }

    public static long enchantCheckInterval() {
        return interval("enchantCheckInterval", ENCHANT_CHECK_INTERVAL);
    }

    public static int enchantShelvesMin() {
        return (int) interval("enchantShelvesMin", ENCHANT_SHELVES_MIN);
    }

    private BoardTuning() {
    }

    private static long interval(String key, long fallback) {
        Long value = DataTables.INSTANCE.board().current().intervals().get(key);
        return value == null || value <= 0 ? fallback : value;
    }

    static String intervalKey(String key) {
        if (!INTERVAL_KEYS.contains(key)) {
            throw new IllegalArgumentException("Неизвестный ключ интервала '" + key + "': допустимы "
                    + INTERVAL_KEYS + ", сверьтесь с BoardTuning.INTERVAL_KEYS");
        }
        return key;
    }
}

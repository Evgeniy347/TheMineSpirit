package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import java.util.Set;

/**
 * Числа доски: значения по умолчанию. Источник истины для работающего мода — JSON-таблица
 * через DataTables (горячая перезагрузка /reload); здесь живут только значения, которые
 * используются, когда таблицы нет, и проверки их осмысленности.
 */
public final class BoardTuning {
    public static final long BOARD_INTERVAL_TICKS = 100;

    public static final int MAX_ACTIVE_PER_SPIRIT = 1;

    public static final int MAX_PENDING = 64;

    public static final int CRATER_TERRITORY_RADIUS = 64;

    public static final long HERD_SCAN_INTERVAL_TICKS = 200;

    public static final int CRATER_BLOCKS_ESTIMATE_RADIUS = 3;

    public static final int MAX_PRIORITY = 100;

    public static final int STOCK_READ_BUDGET = 8;

    public static final int STOCK_BLOCK_BUDGET = 4096;

    public static final long FAIL_BACKOFF_TICKS = 200;

    public static final long CASE_FAIL_PAUSE_TICKS = 1200;

    public static final long CASE_FAIL_MULTIPLIER = 2;

    public static final long CASE_FAIL_PAUSE_CAP_TICKS = 9600;

    public static final int CASE_FAIL_LIMIT = 3;

    public static final int TICKET_FAIL_LIMIT = 5;

    public static final long FENCE_PATROL_INTERVAL = 1000;

    public static final int FENCE_WATCH_RADIUS = 32;

    public static final long ENCHANT_CHECK_INTERVAL = 500;

    public static final int ENCHANT_SHELVES_MIN = 4;

    public static final int TRANSFER_RADIUS_BLOCKS = 32;

    public static final int TRANSFER_MAX_COUNT = 64;

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
            "caseFailMultiplier",
            "caseFailPauseCapTicks",
            "caseFailLimit",
            "ticketFailLimit",
            "fencePatrolInterval",
            "enchantCheckInterval",
            "enchantShelvesMin",
            "transferRadiusBlocks",
            "transferMaxCount");

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

    public static long caseFailMultiplier() {
        long multiplier = interval("caseFailMultiplier", CASE_FAIL_MULTIPLIER);
        return multiplier < 1 ? CASE_FAIL_MULTIPLIER : multiplier;
    }

    public static long caseFailPauseCapTicks() {
        long cap = interval("caseFailPauseCapTicks", CASE_FAIL_PAUSE_CAP_TICKS);
        return Math.max(cap, caseFailPauseTicks());
    }

    public static int caseFailLimit() {
        return (int) interval("caseFailLimit", CASE_FAIL_LIMIT);
    }

    public static int ticketFailLimit() {
        return (int) interval("ticketFailLimit", TICKET_FAIL_LIMIT);
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

    public static int transferRadiusBlocks() {
        return (int) interval("transferRadiusBlocks", TRANSFER_RADIUS_BLOCKS);
    }

    public static int transferMaxCount() {
        return (int) interval("transferMaxCount", TRANSFER_MAX_COUNT);
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

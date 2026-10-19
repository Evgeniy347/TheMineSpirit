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

    /** Каждый следующий провал подряд удлиняет паузу пары дух+кейс в столько раз. */
    public static final long CASE_FAIL_MULTIPLIER = 2;

    /** Пауза пары дух+кейс не растёт выше 9600 тиков (8 мин): иначе один сбойный кейс выключил бы духа навсегда. */
    public static final long CASE_FAIL_PAUSE_CAP_TICKS = 9600;

    /** Столько провалов подряд снимают кейс с этого духа: дальше его берёт другой, а не тот же снова. */
    public static final int CASE_FAIL_LIMIT = 3;

    /** Столько провалов подряд блокируют само задание до изменения условий: иначе цикл переиздаёт его бесконечно. */
    public static final int TICKET_FAIL_LIMIT = 5;

    /** Обзор периметра на дыры в заборе, которые не видит BreakEvent, — раз в 1000 тиков. */

    /** Радиус от дома/духа, в котором сломанный забор считается своим, а не природным. */
    public static final int FENCE_WATCH_RADIUS = 32;

    /** Проверка чаровальни раз в 500 тиков (25 с). */
    public static final long ENCHANT_CHECK_INTERVAL = 500;

    /** Радиус передачи вещей между духами по умолчанию. */
    public static final int TRANSFER_RADIUS_BLOCKS = 32;

    /** Предел предметов в одной передаче по умолчанию. */
    public static final int TRANSFER_MAX_COUNT = 64;

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


    public static long enchantCheckInterval() {
        return interval("enchantCheckInterval", ENCHANT_CHECK_INTERVAL);
    }

    /** Радиус поиска духов-источников передачи: дальше передача не планируется. */
    public static int transferRadiusBlocks() {
        return (int) interval("transferRadiusBlocks", TRANSFER_RADIUS_BLOCKS);
    }

    /** Предел предметов в одной передаче: столько же принимает deliver за один запуск. */
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

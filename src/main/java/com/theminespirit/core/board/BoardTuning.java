package com.theminespirit.core.board;

/**
 * Числа доски: значения по умолчанию. Источник истины для работающего мода — JSON-таблица
 * через DataTables (горячая перезагрузка /reload); здесь живут только значения, которые
 * используются, когда таблицы нет, и проверки их осмысленности.
 */
public final class BoardTuning {
    /** Пересборка доски: раз в 100 тиков (5 с), образец DEMAND_INTERVAL_TICKS. */
    public static final long BOARD_INTERVAL_TICKS = 100;

    /** Срок жизни задания по умолчанию: 5 минут. */
    public static final long DEFAULT_TTL_TICKS = 6000;

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

    private BoardTuning() {
    }
}

package com.theminespirit.core.log;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LogSchema {
    public static final String SOURCE_LIVE = "live";
    public static final String SOURCE_IMPORT = "import";

    private static final String TS = "ts";
    private static final String TICK = "tick";
    private static final String BUILD = "build";
    private static final String SOURCE = "source";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String SPIRIT = "spirit";
    private static final String SPIRIT_ID = "spirit_id";
    private static final String TICKET = "ticket";
    private static final String ACTION = "action";
    private static final String EVENT = "event";
    private static final String CAUSE = "cause";
    private static final String REASON = "reason";
    private static final String NODE = "node";
    private static final String PRIORITY = "priority";
    private static final String SCRIPT = "script";
    private static final String ACTOR_TYPE = "actor_type";
    private static final String ACTOR = "actor";
    private static final String BLOCK_BEFORE = "block_before";
    private static final String BLOCK_AFTER = "block_after";
    private static final String BOARD_CASE = "board_case";

    private static final Map<LogType, Spec> SPECS = specs();

    private LogSchema() {}

    public record Spec(Set<String> required, Set<String> allowed) {
        public Spec {
            required = Set.copyOf(required);
            allowed = Set.copyOf(allowed);
        }
    }

    public static Spec of(LogType type) {
        Spec spec = SPECS.get(type);
        if (spec == null) {
            throw new IllegalArgumentException("У типа '" + type + "' нет описания схемы: запись не собрать");
        }
        return spec;
    }

    public static void validate(
            LogType type, long ts, long tick, String build, String source, Map<String, Object> fields) {
        if (ts <= 0) {
            throw new IllegalArgumentException("Время записи " + ts + " не положительно: ts — мс UTC с 1970 года");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("Тик записи " + tick + " отрицателен: события идут только вперёд");
        }
        if (build == null || build.isBlank()) {
            throw new IllegalArgumentException("Версия сборки пуста: без build нельзя отличить старый лог от нового");
        }
        if (!SOURCE_LIVE.equals(source) && !SOURCE_IMPORT.equals(source)) {
            throw new IllegalArgumentException("Источник записи '" + source + "' неизвестен: допустимы live и import");
        }
        Spec spec = of(type);
        for (String name : spec.required) {
            Object value = fields.get(name);
            if (value == null || (value instanceof String text && text.isBlank())) {
                throw new IllegalArgumentException("У записи '" + type.json() + "' нет обязательного поля '" + name
                        + "': загрузчик не сможет положить её в таблицу " + type.table()
                        + " и ответить «кто, когда, зачем»");
            }
        }
        for (String name : fields.keySet()) {
            if (!spec.allowed.contains(name)) {
                throw new IllegalArgumentException("У записи '" + type.json() + "' поле '" + name
                        + "' не описано схемой: новые поля добавляют в LogSchema и schema.json, "
                        + "иначе они молча пропадут при загрузке");
            }
        }
    }

    private static Map<LogType, Spec> specs() {
        return Map.of(
                LogType.BLOCK_CHANGE,
                blockChange(),
                LogType.DEATH,
                death(),
                LogType.TASK_EVENT,
                taskEvent(),
                LogType.GRAPH_DECISION,
                graphDecision(),
                LogType.ROUTE,
                route(),
                LogType.STUCK,
                stuck(),
                LogType.ITEM_MOVE,
                itemMove(),
                LogType.SPIRIT_SAMPLE,
                spiritSample(),
                LogType.SERVER_HEALTH,
                serverHealth(),
                LogType.MESSAGE,
                message());
    }

    private static Spec blockChange() {
        return spec(
                Set.of(ACTOR_TYPE, ACTOR, ACTION, BLOCK_BEFORE, BLOCK_AFTER, X, Y, Z),
                ACTOR_TYPE,
                ACTOR,
                TICKET,
                ACTION,
                BLOCK_BEFORE,
                BLOCK_AFTER,
                X,
                Y,
                Z,
                "basis",
                CAUSE,
                REASON);
    }

    private static Spec death() {
        return spec(
                Set.of(EVENT, SPIRIT, X, Y, Z, CAUSE),
                EVENT,
                SPIRIT,
                SPIRIT_ID,
                X,
                Y,
                Z,
                CAUSE,
                REASON,
                "killer",
                "inventory",
                TICKET,
                SCRIPT,
                "respawn_x",
                "respawn_y",
                "respawn_z",
                "respawn_ts",
                "recovered");
    }

    private static Spec taskEvent() {
        return spec(
                Set.of(TICKET, BOARD_CASE, EVENT),
                TICKET,
                BOARD_CASE,
                EVENT,
                SPIRIT,
                SPIRIT_ID,
                PRIORITY,
                "attempts",
                CAUSE,
                REASON,
                "missing",
                "target_x",
                "target_y",
                "target_z",
                "source_node");
    }

    private static Spec graphDecision() {
        return spec(
                Set.of(NODE, SPIRIT, PRIORITY, "feasible", "chosen"),
                NODE,
                SPIRIT,
                SPIRIT_ID,
                PRIORITY,
                "feasible",
                "chosen",
                "next_node",
                "why",
                CAUSE);
    }

    private static Spec route() {
        return spec(
                Set.of(SPIRIT, "purpose", "from_x", "from_y", "from_z", "to_x", "to_y", "to_z", "status"),
                SPIRIT,
                SPIRIT_ID,
                "purpose",
                "from_x",
                "from_y",
                "from_z",
                "to_x",
                "to_y",
                "to_z",
                "status",
                "nodes",
                "ms",
                "end_x",
                "end_y",
                "end_z",
                REASON,
                CAUSE);
    }

    private static Spec stuck() {
        return spec(
                Set.of(SPIRIT, EVENT),
                SPIRIT,
                SPIRIT_ID,
                EVENT,
                X,
                Y,
                Z,
                "method",
                "helper",
                REASON,
                CAUSE,
                "since_tick");
    }

    private static Spec itemMove() {
        return spec(
                Set.of(SPIRIT, ACTION, "item", "count"),
                SPIRIT,
                SPIRIT_ID,
                ACTION,
                "item",
                "count",
                "container_x",
                "container_y",
                "container_z",
                TICKET);
    }

    private static Spec spiritSample() {
        return spec(
                Set.of(SPIRIT, X, Y, Z, "state"),
                SPIRIT,
                SPIRIT_ID,
                X,
                Y,
                Z,
                "state",
                SCRIPT,
                TICKET,
                "food",
                "health");
    }

    private static Spec serverHealth() {
        return spec(
                Set.of("mspt", "players"),
                "mspt",
                "worst_mspt",
                "players",
                "source_failures",
                "pending",
                "active",
                "blocked",
                "board_ms",
                "prune_ms",
                "tickets",
                EVENT);
    }

    private static Spec message() {
        return spec(Set.of("level", "text"), "level", "tag", "class", "text");
    }

    private static Spec spec(Set<String> required, String... allowed) {
        Set<String> names = new LinkedHashSet<>();
        names.add(TS);
        names.add(TICK);
        names.add(BUILD);
        names.add(SOURCE);
        names.addAll(List.of(allowed));
        return new Spec(required, names);
    }
}

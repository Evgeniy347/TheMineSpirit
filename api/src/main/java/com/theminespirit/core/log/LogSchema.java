package com.theminespirit.core.log;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LogSchema {
    public static final String SOURCE_LIVE = "live";
    public static final String SOURCE_IMPORT = "import";

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
                spec(
                        Set.of("actor_type", "actor", "action", "block_before", "block_after", "x", "y", "z"),
                        "actor_type",
                        "actor",
                        "ticket",
                        "action",
                        "block_before",
                        "block_after",
                        "x",
                        "y",
                        "z",
                        "basis",
                        "cause",
                        "reason"),
                LogType.DEATH,
                spec(
                        Set.of("event", "spirit", "x", "y", "z", "cause"),
                        "event",
                        "spirit",
                        "spirit_id",
                        "x",
                        "y",
                        "z",
                        "cause",
                        "reason",
                        "killer",
                        "inventory",
                        "ticket",
                        "script",
                        "respawn_x",
                        "respawn_y",
                        "respawn_z",
                        "respawn_ts",
                        "recovered"),
                LogType.TASK_EVENT,
                spec(
                        Set.of("ticket", "board_case", "event"),
                        "ticket",
                        "board_case",
                        "event",
                        "spirit",
                        "spirit_id",
                        "priority",
                        "attempts",
                        "cause",
                        "reason",
                        "missing",
                        "target_x",
                        "target_y",
                        "target_z",
                        "source_node"),
                LogType.GRAPH_DECISION,
                spec(
                        Set.of("node", "spirit", "priority", "feasible", "chosen"),
                        "node",
                        "spirit",
                        "spirit_id",
                        "priority",
                        "feasible",
                        "chosen",
                        "next_node",
                        "why",
                        "cause"),
                LogType.ROUTE,
                spec(
                        Set.of("spirit", "purpose", "from_x", "from_y", "from_z", "to_x", "to_y", "to_z", "status"),
                        "spirit",
                        "spirit_id",
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
                        "reason",
                        "cause"),
                LogType.STUCK,
                spec(
                        Set.of("spirit", "event"),
                        "spirit",
                        "spirit_id",
                        "event",
                        "x",
                        "y",
                        "z",
                        "method",
                        "helper",
                        "reason",
                        "cause",
                        "since_tick"),
                LogType.ITEM_MOVE,
                spec(
                        Set.of("spirit", "action", "item", "count"),
                        "spirit",
                        "spirit_id",
                        "action",
                        "item",
                        "count",
                        "container_x",
                        "container_y",
                        "container_z",
                        "ticket"),
                LogType.SPIRIT_SAMPLE,
                spec(
                        Set.of("spirit", "x", "y", "z", "state"),
                        "spirit",
                        "spirit_id",
                        "x",
                        "y",
                        "z",
                        "state",
                        "script",
                        "ticket",
                        "food",
                        "health"),
                LogType.SERVER_HEALTH,
                spec(
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
                        "event"),
                LogType.MESSAGE,
                spec(Set.of("level", "text"), "level", "tag", "class", "text"));
    }

    private static Spec spec(Set<String> required, String... allowed) {
        Set<String> names = new LinkedHashSet<>();
        names.add("ts");
        names.add("tick");
        names.add("build");
        names.add("source");
        names.addAll(List.of(allowed));
        return new Spec(required, names);
    }
}

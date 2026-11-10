package com.theminespirit.core.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogScenarioTests {
    @TempDir
    Path directory;

    @Test
    void scenario_ThreeSpiritsWholeDay_EveryTableGetsTypedRows() throws Exception {
        try (EventLog log = new EventLog(directory, "1.0+test", "live")) {
            List<String> spirits = List.of("дух-1", "дух-2", "дух-3");
            for (int index = 0; index < spirits.size(); index++) {
                String spirit = spirits.get(index);
                appendBlock(log, spirit, index);
                appendTask(log, spirit, index);
                appendGraph(log, spirit, index);
                appendRoute(log, spirit, index);
                appendStuck(log, spirit, index);
                appendItem(log, spirit, index);
                appendSample(log, spirit, index);
                appendDeath(log, spirit, index);
            }
            appendHealth(log);
            appendMessage(log);
        }

        List<Map<String, Object>> rows = readRows();
        Map<LogType, Integer> counts = new EnumMap<>(LogType.class);
        for (Map<String, Object> row : rows) {
            counts.merge(typeOf(String.valueOf(row.get("type"))), 1, Integer::sum);
        }

        for (LogType type : LogType.values()) {
            assertTrue(
                    counts.getOrDefault(type, 0) > 0,
                    "Крупный сценарий обязан покрыть таблицу " + type.table() + ": иначе поиск по ней пуст");
        }
        assertEquals(
                3,
                counts.get(LogType.SPIRIT_SAMPLE),
                "Каждый из трёх духов обязан оставить выборку состояния: по ней отвечает команда where");
        assertTrue(
                rows.stream().anyMatch(row -> "refuse".equals(row.get("action"))),
                "Отказ в изменении блока обязан попасть в block_changes: без него вопрос «почему не сломал» не решается");
    }

    private static void appendBlock(EventLog log, String spirit, int index) {
        log.append(LogRecord.builder(LogType.BLOCK_CHANGE, 1000L + index, 10L + index, "1.0+test", "live")
                .put("actor_type", "spirit")
                .put("actor", spirit)
                .put("action", index == 2 ? "refuse" : "break")
                .put("block_before", "Block{minecraft:oak_log}[axis=y]")
                .put("block_after", index == 2 ? "Block{minecraft:oak_log}[axis=y]" : "Block{minecraft:air}")
                .at(-181 + index, 68, -518)
                .put("basis", "задание graph:test")
                .cause(index == 2 ? LogCause.REFUSE : LogCause.BREAK)
                .put("reason", index == 2 ? "клетка постройки" : "")
                .build());
    }

    private static void appendTask(EventLog log, String spirit, int index) {
        for (String event : List.of("POSTED", "ASSIGNED", "FAILED", "DONE")) {
            log.append(LogRecord.builder(LogType.TASK_EVENT, 1100L + index, 20L + index, "1.0+test", "live")
                    .ticket("ticket-" + index)
                    .put("board_case", "GATHER_MATERIALS")
                    .put("event", event)
                    .spirit(spirit, "id-" + index)
                    .put("priority", 40)
                    .put("attempts", 1)
                    .cause(LogCause.FAILED)
                    .put("reason", "FAILED".equals(event) ? "нет пути к блоку" : "")
                    .put("missing", "{\"minecraft:oak_log\":4}")
                    .put("source_node", "graph:test")
                    .build());
        }
    }

    private static void appendGraph(EventLog log, String spirit, int index) {
        log.append(LogRecord.builder(LogType.GRAPH_DECISION, 1200L + index, 30L + index, "1.0+test", "live")
                .put("node", "dig_out")
                .spirit(spirit, "id-" + index)
                .put("priority", 70)
                .put("feasible", true)
                .put("chosen", true)
                .put("next_node", "gather_wood")
                .put("why", "вес выше на 5")
                .cause(LogCause.CHOSEN)
                .build());
    }

    private static void appendRoute(EventLog log, String spirit, int index) {
        log.append(LogRecord.builder(LogType.ROUTE, 1300L + index, 40L + index, "1.0+test", "live")
                .spirit(spirit, "id-" + index)
                .put("purpose", "dig_out")
                .put("from_x", -181)
                .put("from_y", 68)
                .put("from_z", -518)
                .put("to_x", -175)
                .put("to_y", 66)
                .put("to_z", -510)
                .put("status", "FOUND")
                .put("nodes", 116)
                .put("ms", 30)
                .put("end_x", -175)
                .put("end_y", 66)
                .put("end_z", -510)
                .put("reason", "")
                .cause(LogCause.CHOSEN)
                .build());
    }

    private static void appendStuck(EventLog log, String spirit, int index) {
        log.append(LogRecord.builder(LogType.STUCK, 1400L + index, 50L + index, "1.0+test", "live")
                .spirit(spirit, "id-" + index)
                .put("event", "mark")
                .at(-181, 68, -518)
                .put("method", "no_progress")
                .put("helper", "")
                .put("reason", "не двигается 300 тиков")
                .cause(LogCause.STUCK)
                .put("since_tick", 40)
                .build());
    }

    private static void appendItem(EventLog log, String spirit, int index) {
        log.append(LogRecord.builder(LogType.ITEM_MOVE, 1500L + index, 60L + index, "1.0+test", "live")
                .spirit(spirit, "id-" + index)
                .put("action", "take")
                .put("item", "minecraft:bread")
                .put("count", 3)
                .put("container_x", -177)
                .put("container_y", 69)
                .put("container_z", -518)
                .ticket("ticket-" + index)
                .build());
    }

    private static void appendSample(EventLog log, String spirit, int index) {
        log.append(LogRecord.builder(LogType.SPIRIT_SAMPLE, 1600L + index, 70L + index, "1.0+test", "live")
                .spirit(spirit, "id-" + index)
                .at(-181, 68, -518)
                .put("state", "RUNNING")
                .put("script", "gather")
                .ticket("ticket-" + index)
                .put("food", 18)
                .put("health", 20.0)
                .build());
    }

    private static void appendDeath(EventLog log, String spirit, int index) {
        log.append(LogRecord.builder(LogType.DEATH, 1650L + index, 80L + index, "1.0+test", "live")
                .put("event", "death")
                .spirit(spirit, "id-" + index)
                .at(-181, 68, -518)
                .cause(LogCause.DEATH)
                .put("killer", "minecraft:zombie")
                .put("inventory", "{\"minecraft:bread\":3}")
                .ticket("ticket-" + index)
                .put("script", "gather")
                .put("recovered", false)
                .build());
    }

    private static void appendHealth(EventLog log) {
        log.append(LogRecord.builder(LogType.SERVER_HEALTH, 1700, 1200, "1.0+test", "live")
                .put("mspt", 12.5)
                .put("worst_mspt", 31.0)
                .put("players", 2)
                .put("source_failures", 0)
                .put("pending", 3)
                .put("active", 2)
                .put("blocked", 1)
                .put("board_ms", 4)
                .put("prune_ms", 1)
                .put("tickets", 6)
                .put("event", "sample")
                .build());
    }

    private static void appendMessage(EventLog log) {
        log.append(LogRecord.builder(LogType.MESSAGE, 1800, 1200, "1.0+test", "live")
                .put("level", "WARN")
                .put("tag", "test")
                .put("class", LogScenarioTests.class.getName())
                .put("text", "текстовая строка для поиска")
                .build());
    }

    private List<Map<String, Object>> readRows() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .toList()) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        rows.add(TestJson.read(line));
                    }
                }
            }
        }
        return rows;
    }

    private static LogType typeOf(String json) {
        for (LogType type : LogType.values()) {
            if (type.json().equals(json)) {
                return type;
            }
        }
        throw new IllegalArgumentException("В JSONL неизвестный тип: " + json);
    }
}

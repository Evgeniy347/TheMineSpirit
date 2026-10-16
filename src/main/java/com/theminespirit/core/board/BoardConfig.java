package com.theminespirit.core.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Живые числа доски: приоритеты кейсов, сроки жизни (TTL) и интервалы. Читается из
 * JSON-таблицы через {@code DataTables} — правится датапаком мира и перезагружается
 * по {@code /reload} без перезапуска. Ключи приоритетов и TTL — имена {@link BoardCase},
 * ключи интервалов — {@link BoardTuning#INTERVAL_KEYS}. Пустые карты допустимы: недостающее
 * добирают значения по умолчанию из {@code BoardRules} и {@code BoardTuning}.
 */
public record BoardConfig(Map<String, Integer> priorities, Map<String, Long> ttl, Map<String, Long> intervals) {
    public static final String RESOURCE = "data/theminespirit/board/board.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public BoardConfig {
        Objects.requireNonNull(priorities,
                "Приоритеты не могут быть null: пустую таблицу задают пустой картой, а не null");
        priorities = Map.copyOf(priorities);
        Objects.requireNonNull(ttl, "TTL не может быть null: пустую таблицу задают пустой картой, а не null");
        ttl = Map.copyOf(ttl);
        Objects.requireNonNull(intervals,
                "Интервалы не могут быть null: пустую таблицу задают пустой картой, а не null");
        intervals = Map.copyOf(intervals);
    }

    public static BoardConfig fromJson(String json, String origin) {
        Objects.requireNonNull(json, "Текст таблицы не может быть null: разбирать нечего");
        Objects.requireNonNull(origin, "Источник таблицы не может быть null: сообщение об ошибке называет путь");
        JsonNode root = JsonResource.parse(json, origin);
        if (!root.isObject()) {
            throw new IllegalStateException("Таблица '" + origin + "' — не объект: на верхнем уровне ожидается "
                    + "объект «priorities → ttl → intervals»");
        }
        Map<String, Integer> priorities = integers(root.get("priorities"), origin, "priorities", BoardCase::valueOf);
        Map<String, Long> ttl = longs(root.get("ttl"), origin, "ttl", BoardCase::valueOf);
        Map<String, Long> intervals = longs(root.get("intervals"), origin, "intervals", BoardTuning::intervalKey);
        return new BoardConfig(priorities, ttl, intervals);
    }

    public static BoardConfig readFromClasspath() {
        return fromJson(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    private static Map<String, Integer> integers(JsonNode node, String origin, String field,
            Function<String, ?> known) {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return values;
        }
        if (!node.isObject()) {
            throw new IllegalStateException("Поле '" + field + "' в '" + origin + "' — не объект: ожидается "
                    + "карта «имя → число»");
        }
        Iterator<Map.Entry<String, JsonNode>> entries = node.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String key = entry.getKey();
            checkKey(known, key, origin, field);
            JsonNode value = entry.getValue();
            if (!value.isIntegralNumber()) {
                throw new IllegalStateException("Поле '" + field + "': ключ '" + key
                        + "' не целое число, получено " + value.getNodeType()
                        + ": приоритет сравнивается числом, строка сломает сортировку выдачи");
            }
            int priority = value.intValue();
            if (priority < 0) {
                throw new IllegalStateException("Поле '" + field + "': ключ '" + key + "' равен " + priority
                        + " и отрицателен: приоритет вне [0, " + BoardTuning.MAX_PRIORITY
                        + "] не участвует в сравнении выдачи");
            }
            values.put(key, priority);
        }
        return values;
    }

    private static Map<String, Long> longs(JsonNode node, String origin, String field,
            Function<String, ?> known) {
        Map<String, Long> values = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return values;
        }
        if (!node.isObject()) {
            throw new IllegalStateException("Поле '" + field + "' в '" + origin + "' — не объект: ожидается "
                    + "карта «имя → число»");
        }
        Iterator<Map.Entry<String, JsonNode>> entries = node.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String key = entry.getKey();
            checkKey(known, key, origin, field);
            JsonNode value = entry.getValue();
            if (!value.isIntegralNumber()) {
                throw new IllegalStateException("Поле '" + field + "': ключ '" + key
                        + "' не целое число, получено " + value.getNodeType()
                        + ": интервал считается в тиках, строка сломает пересборку доски");
            }
            long number = value.longValue();
            if (number < 0) {
                throw new IllegalStateException("Поле '" + field + "': ключ '" + key + "' равен " + number
                        + " и отрицателен: интервал и TTL не бывают отрицательными");
            }
            values.put(key, number);
        }
        return values;
    }

    private static void checkKey(Function<String, ?> known, String key, String origin,
            String field) {
        try {
            known.apply(key);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalStateException("Поле '" + field + "' в '" + origin + "': ключ '" + key
                    + "' неизвестен: опечатка в имени молча потеряла бы строку таблицы", unknown);
        }
    }
}

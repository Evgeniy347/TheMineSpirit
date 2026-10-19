package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.reload.DataTables;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.tactics.Situation;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

public record TacticsAdapterSettings(int evalIntervalTicks, int maxEnemies, int factRadius, int factChunkBudget,
        int retreatHoldTicks, int journalCapacity, int journalMaxPerSecond) {
    public static final int DEFAULT_EVAL_INTERVAL_TICKS = 10;
    public static final int DEFAULT_MAX_ENEMIES = Situation.MAX_ENEMIES;
    public static final int DEFAULT_FACT_RADIUS = 32;
    public static final int DEFAULT_FACT_CHUNK_BUDGET = 64;
    public static final int DEFAULT_RETREAT_HOLD_TICKS = 200;
    public static final int DEFAULT_JOURNAL_CAPACITY = 32;
    public static final int DEFAULT_JOURNAL_MAX_PER_SECOND = 1;

    public static final int MAX_EVAL_INTERVAL_TICKS = 6000;
    public static final int MAX_FACT_RADIUS = 256;
    public static final int MAX_FACT_CHUNK_BUDGET = 1024;
    public static final int MAX_RETREAT_HOLD_TICKS = 6000;
    public static final int MAX_JOURNAL_CAPACITY = 256;
    public static final int MAX_JOURNAL_MAX_PER_SECOND = DecisionJournal.TICKS_PER_SECOND;

    private static final String FILE = "adapter.json";
    private static final String RESOURCE = "/data/theminespirit/tactics/" + FILE;
    private static final Set<String> FIELDS = Set.of("evalIntervalTicks", "maxEnemies", "factRadius",
            "factChunkBudget", "retreatHoldTicks", "journalCapacity", "journalMaxPerSecond");
    private static final ObjectMapper MAPPER = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    public TacticsAdapterSettings {
        range("evalIntervalTicks", evalIntervalTicks, 1, MAX_EVAL_INTERVAL_TICKS);
        range("maxEnemies", maxEnemies, 1, Situation.MAX_ENEMIES);
        range("factRadius", factRadius, 1, MAX_FACT_RADIUS);
        range("factChunkBudget", factChunkBudget, 1, MAX_FACT_CHUNK_BUDGET);
        range("retreatHoldTicks", retreatHoldTicks, 1, MAX_RETREAT_HOLD_TICKS);
        range("journalCapacity", journalCapacity, 1, MAX_JOURNAL_CAPACITY);
        range("journalMaxPerSecond", journalMaxPerSecond, 1, MAX_JOURNAL_MAX_PER_SECOND);
    }

    public static TacticsAdapterSettings defaults() {
        return new TacticsAdapterSettings(DEFAULT_EVAL_INTERVAL_TICKS, DEFAULT_MAX_ENEMIES, DEFAULT_FACT_RADIUS,
                DEFAULT_FACT_CHUNK_BUDGET, DEFAULT_RETREAT_HOLD_TICKS, DEFAULT_JOURNAL_CAPACITY,
                DEFAULT_JOURNAL_MAX_PER_SECOND);
    }

    public static TacticsAdapterSettings loadDefault() {
        return DataTables.INSTANCE.adapter().current();
    }

    public static TacticsAdapterSettings readFromClasspath() {
        return fromJson(readResource());
    }

    public static TacticsAdapterSettings fromJson(String json) {
        JsonNode root = parse(json);
        if (!root.isObject()) {
            throw new IllegalStateException(FILE + " → (корень): ожидался объект { ... }. Проверьте: схему adapter.json");
        }
        for (Iterator<String> names = root.fieldNames(); names.hasNext();) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                throw fieldFail(name, "неизвестное поле, допустимы " + FIELDS);
            }
        }
        try {
            return new TacticsAdapterSettings(readInt(root, "evalIntervalTicks"), readInt(root, "maxEnemies"),
                    readInt(root, "factRadius"), readInt(root, "factChunkBudget"), readInt(root, "retreatHoldTicks"),
                    readInt(root, "journalCapacity"), readInt(root, "journalMaxPerSecond"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(FILE + " → " + error.getMessage()
                    + ". Проверьте: значение в границах, которые держит конструктор TacticsAdapterSettings", error);
        }
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(FILE + " → (корень): JSON не разобран (" + error.getOriginalMessage()
                    + "). Проверьте: синтаксис, кавычки, запятые, дубли ключей и кодировку UTF-8", error);
        }
    }

    private static int readInt(JsonNode root, String name) {
        JsonNode node = root.get(name);
        if (node == null) {
            throw fieldFail(name, "нет обязательного поля, добавьте его в adapter.json");
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw fieldFail(name, "ожидалось целое число без дробной части и кавычек, получено " + node);
        }
        return node.asInt();
    }

    private static IllegalStateException fieldFail(String field, String problem) {
        return new IllegalStateException(FILE + " → " + field + ": " + problem + ". Проверьте: схему adapter.json");
    }

    private static void range(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("TacticsAdapterSettings." + field + " = " + value + ": допустимо от "
                    + min + " до " + max);
        }
    }

    private static String readResource() {
        try (InputStream stream = TacticsAdapterSettings.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Ресурс адаптера тактики " + RESOURCE
                        + " не найден: проверьте, что файл лежит в src/main/resources/data/theminespirit/tactics");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Ресурс адаптера тактики " + FILE
                    + " не прочитан: проверьте права и кодировку UTF-8", error);
        }
    }

    }

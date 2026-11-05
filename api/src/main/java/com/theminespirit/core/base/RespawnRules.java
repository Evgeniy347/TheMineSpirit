package com.theminespirit.core.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public record RespawnRules(int delayTicks) {
    public static final String RESOURCE = "data/theminespirit/base/respawn.json";
    public static final int DEFAULT_DELAY_TICKS = 600;

    private static final String DELAY_TICKS = "delayTicks";
    private static final Set<String> FIELDS = Set.of(DELAY_TICKS);
    private static volatile Supplier<RespawnRules> source = RespawnRules::defaults;

    public RespawnRules {
        if (delayTicks <= 0) {
            throw new IllegalArgumentException("Задержка возрождения " + delayTicks
                    + " не положительна: дух не может возродиться в тот же тик, что погиб");
        }
    }

    public static RespawnRules defaults() {
        return new RespawnRules(DEFAULT_DELAY_TICKS);
    }

    public static RespawnRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static RespawnRules loadDefault() {
        return source.get();
    }

    public static void useSource(Supplier<RespawnRules> supplier) {
        source = Objects.requireNonNull(
                supplier, "Источник правил возрождения не может быть null: без него загрузка таблицы не видна");
    }

    public static RespawnRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "respawn.json");
        int delayTicks = JsonResource.integer(root.get(DELAY_TICKS), origin, DELAY_TICKS);
        try {
            return new RespawnRules(delayTicks);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }
}

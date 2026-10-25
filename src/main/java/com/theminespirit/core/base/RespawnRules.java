package com.theminespirit.core.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.DataTables;
import java.util.Set;

/**
 * Числа возрождения духа: задержка после смерти. Значения по умолчанию — для случая, когда таблицы нет; работающий
 * мод берёт версию из {@link DataTables} (горячая перезагрузка /reload), поэтому 600 тиков живут в json, а не в коде.
 */
public record RespawnRules(int delayTicks) {
    public static final String RESOURCE = "data/theminespirit/base/respawn.json";
    public static final int DEFAULT_DELAY_TICKS = 600;

    private static final String DELAY_TICKS = "delayTicks";
    private static final Set<String> FIELDS = Set.of(DELAY_TICKS);

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
        return DataTables.INSTANCE.respawn().current();
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

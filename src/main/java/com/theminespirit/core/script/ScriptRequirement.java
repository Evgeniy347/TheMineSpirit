package com.theminespirit.core.script;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ScriptRequirement(String kind, String tool, String param, Map<String, Integer> tiers, int fallback) {

    public static final String TOOL = "tool";
    public static final int NO_TIER = 0;
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 4;

    public ScriptRequirement {
        if (!TOOL.equals(kind)) {
            throw new IllegalArgumentException(
                    "Вид требования '" + kind + "' неизвестен: каталог понимает только '" + TOOL + "'");
        }
        if (tool == null || tool.isBlank()) {
            throw new IllegalArgumentException(
                    "Инструмент требования пуст: без него проверка плана не знает, что искать");
        }
        if (param == null || param.isBlank()) {
            throw new IllegalArgumentException(
                    "Параметр требования пуст: без него требование не привязать к шагу плана");
        }
        Objects.requireNonNull(tiers, "tiers не может быть null: пустой набор задают пустой картой");
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : tiers.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("Ключ tiers пуст: по имени значения параметра выбирается уровень");
            }
            if (entry.getValue() == null || entry.getValue() < MIN_TIER || entry.getValue() > MAX_TIER) {
                throw new IllegalArgumentException("Уровень инструмента для '" + entry.getKey() + "' равен "
                        + entry.getValue() + ": допустимо от " + MIN_TIER + " до " + MAX_TIER);
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        tiers = Collections.unmodifiableMap(copy);
        if (fallback < NO_TIER || fallback > MAX_TIER) {
            throw new IllegalArgumentException(
                    "Запасной уровень требования равен " + fallback + ": допустимо от " + NO_TIER + " до " + MAX_TIER);
        }
    }

    public static ScriptRequirement pickaxe(String param, Map<String, Integer> tiers, int fallback) {
        return new ScriptRequirement(TOOL, "pickaxe", param, tiers, fallback);
    }
}

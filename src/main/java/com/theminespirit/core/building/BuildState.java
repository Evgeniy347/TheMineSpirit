package com.theminespirit.core.building;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record BuildState(int placed, int removed, int skipped, int failed, boolean unfinished) {
    public BuildState {
        if (placed < 0 || removed < 0 || skipped < 0 || failed < 0) {
            throw new IllegalArgumentException("Счётчики прогресса не бывают отрицательными: placed=" + placed + ", removed=" + removed
                    + ", skipped=" + skipped + ", failed=" + failed);
        }
    }

    public static BuildState fresh() {
        return new BuildState(0, 0, 0, 0, true);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("placed", placed);
        map.put("removed", removed);
        map.put("skipped", skipped);
        map.put("failed", failed);
        map.put("unfinished", unfinished);
        return Map.copyOf(map);
    }

    public static BuildState fromMap(Map<String, Object> map) {
        Objects.requireNonNull(map, "Снимок не может быть null: пустое состояние задают пустой картой");
        return new BuildState(number(map, "placed"), number(map, "removed"), number(map, "skipped"), number(map, "failed"),
                unfinished(map));
    }

    private static int number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Поле снимка '" + key + "' обязано быть числом: иначе прогресс не восстановить");
        }
        int parsed = number.intValue();
        if (parsed < 0) {
            throw new IllegalArgumentException("Поле снимка '" + key + "' отрицательно: счётчики не бывают меньше нуля");
        }
        return parsed;
    }

    private static boolean unfinished(Map<String, Object> map) {
        Object value = map.get("unfinished");
        if (value == null) {
            return true;
        }
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException("Поле снимка 'unfinished' обязано быть true или false");
        }
        return flag;
    }
}

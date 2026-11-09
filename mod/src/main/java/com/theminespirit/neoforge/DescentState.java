package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.mining.DescentDirection;
import com.theminespirit.core.mining.DescentPlan;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class DescentState {
    record Snapshot(BlockCoord entry, DescentDirection direction, int targetY, int stepIndex, int blocksDug) {}

    private DescentState() {}

    static Map<String, Object> save(BlockCoord entry, DescentPlan plan, int stepIndex, int blocksDug) {
        Map<String, Object> map = new HashMap<>();
        map.put("entry_x", entry.x());
        map.put("entry_y", entry.y());
        map.put("entry_z", entry.z());
        map.put("direction", plan.direction().name().toLowerCase(Locale.ROOT));
        map.put("target_y", plan.targetY());
        map.put("step", stepIndex);
        map.put("blocks_dug", blocksDug);
        return Map.copyOf(map);
    }

    static Snapshot read(Map<String, Object> saved) {
        BlockCoord entry = new BlockCoord(number(saved, "entry_x"), number(saved, "entry_y"), number(saved, "entry_z"));
        DescentDirection direction = DescentDirection.parse(text(saved, "direction"));
        int targetY = number(saved, "target_y");
        int blocksDug = number(saved, "blocks_dug");
        int stepIndex = number(saved, "step");
        return new Snapshot(entry, direction, targetY, stepIndex, blocksDug);
    }

    static Map<String, Object> result(
            BlockCoord entry, DescentPlan plan, int stepIndex, int blocksDug, @Nullable String stopReason) {
        int floorUnderFeet = entry.y() - 1;
        int y = stepIndex == 0
                ? floorUnderFeet
                : plan.steps().get(stepIndex - 1).floor();
        int depth = floorUnderFeet - y;
        return Map.of(
                "reached", stopReason == null,
                "y", y,
                "depth", depth,
                "blocks_dug", blocksDug,
                "reason", stopReason == null ? "" : stopReason);
    }

    static ScriptInstance.Progress progress(int stepIndex, DescentPlan plan) {
        return new ScriptInstance.Progress(stepIndex, Math.max(plan.steps().size(), 1));
    }

    private static int number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Поле снимка '" + key + "' отсутствует: без него спуск не восстановить");
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Поле снимка '" + key + "' обязано быть числом: иначе спуск не восстановить");
        }
        return number.intValue();
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(
                    "Поле снимка '" + key + "' обязано быть строкой: иначе спуск не восстановить");
        }
        return text;
    }
}

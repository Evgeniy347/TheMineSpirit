package com.theminespirit.neoforge;

import com.theminespirit.core.mining.DescentDirection;
import com.theminespirit.core.mining.SafeMineMode;
import com.theminespirit.core.mining.SafeMinePlan;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class SafeMineState {
    record Restored(
            BlockCoord entry,
            SafeMineMode mode,
            DescentDirection direction,
            int torchInterval,
            int plannedSteps,
            int step,
            int dug,
            int filled,
            int torches) {}

    static Map<String, Object> save(
            SafeMinePlan plan, int stepIndex, int blocksDug, int blocksFilled, int torchesPlaced) {
        Map<String, Object> map = new HashMap<>();
        map.put("entry_x", plan.entry().x());
        map.put("entry_y", plan.entry().y());
        map.put("entry_z", plan.entry().z());
        map.put("mode", plan.mode().name().toLowerCase(Locale.ROOT));
        map.put("direction", plan.direction().name().toLowerCase(Locale.ROOT));
        map.put("torch_interval", plan.torchInterval());
        map.put("step", stepIndex);
        map.put("blocks_dug", blocksDug);
        map.put("blocks_filled", blocksFilled);
        map.put("torches", torchesPlaced);
        map.put("planned_steps", plan.steps().size());
        return Map.copyOf(map);
    }

    static Restored restore(Map<String, Object> saved) {
        Objects.requireNonNull(saved, "Снимок состояния не может быть null: пустое состояние задают пустой картой");
        BlockCoord entry = new BlockCoord(number(saved, "entry_x"), number(saved, "entry_y"), number(saved, "entry_z"));
        SafeMineMode mode = SafeMineMode.parse(text(saved, "mode"));
        DescentDirection direction = DescentDirection.parse(text(saved, "direction"));
        int torchInterval = number(saved, "torch_interval");
        int plannedSteps = number(saved, "planned_steps");
        int dug = number(saved, "blocks_dug");
        int filled = number(saved, "blocks_filled");
        int torches = number(saved, "torches");
        int step = number(saved, "step");
        return new Restored(entry, mode, direction, torchInterval, plannedSteps, step, dug, filled, torches);
    }

    private static int number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Поле снимка '" + key + "' отсутствует: без него проходку не восстановить");
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Поле снимка '" + key + "' обязано быть числом: иначе проходку не восстановить");
        }
        return number.intValue();
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(
                    "Поле снимка '" + key + "' обязано быть строкой: иначе проходку не восстановить");
        }
        return text;
    }
}

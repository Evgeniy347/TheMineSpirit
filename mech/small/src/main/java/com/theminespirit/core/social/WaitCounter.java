package com.theminespirit.core.social;

import java.util.Map;
import java.util.Objects;

public final class WaitCounter {
    public static final String TARGET_KEY = "target";
    public static final String ELAPSED_KEY = "elapsed";

    private final int target;
    private int elapsed;

    private WaitCounter(int target, int elapsed) {
        if (target < 0) {
            throw new IllegalArgumentException("Время ожидания " + target + " меньше 0: счётчик не может ждать "
                    + "отрицательное число тиков, иначе он завершится раньше, чем начнётся");
        }
        if (elapsed < 0) {
            throw new IllegalArgumentException("Прошедшее время " + elapsed + " меньше 0: снимок не может содержать "
                    + "отрицательный счётчик тиков");
        }
        this.target = target;
        this.elapsed = elapsed;
    }

    public static WaitCounter start(int ticks) {
        return new WaitCounter(ticks, 0);
    }

    public boolean tick() {
        elapsed++;
        return elapsed >= target;
    }

    public int remaining() {
        return Math.max(target - elapsed, 0);
    }

    public int elapsed() {
        return elapsed;
    }

    public int target() {
        return target;
    }

    public Map<String, Object> toMap() {
        return Map.of(TARGET_KEY, target, ELAPSED_KEY, elapsed);
    }

    public static WaitCounter fromMap(Map<String, Object> state) {
        Objects.requireNonNull(state, "state не может быть null: снимок состояния приходит картой");
        return new WaitCounter(read(state, TARGET_KEY), read(state, ELAPSED_KEY));
    }

    private static int read(Map<String, Object> state, String key) {
        Object value = state.get(key);
        if (value == null) {
            return 0;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Состояние ожидания: '" + key + "' = " + value
                    + ", а ожидалось целое число: снимок задачи повреждён или записан другой версией мода");
        }
        return number.intValue();
    }
}

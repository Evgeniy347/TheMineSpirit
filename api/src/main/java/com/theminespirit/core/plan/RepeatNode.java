package com.theminespirit.core.plan;

import java.util.Objects;

public record RepeatNode(int times, TaskNode step) implements TaskNode {
    public RepeatNode {
        if (times < 0) {
            throw new IllegalArgumentException(
                    "times не может быть отрицательным: повторение выполняется 0 или более раз");
        }
        Objects.requireNonNull(step, "step не может быть null: повторять нечего");
    }
}

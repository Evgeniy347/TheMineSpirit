package com.theminespirit.core.intellect;

import java.util.Objects;
import java.util.Set;

public final class BackgroundWork {
    private static final Set<String> PROTECTED = Set.of("sleep");

    private BackgroundWork() {}

    public static boolean preemptible(String script) {
        Objects.requireNonNull(script, "Скрипт занятия не может быть null: вытесняемость решается по имени");
        return !PROTECTED.contains(script);
    }
}

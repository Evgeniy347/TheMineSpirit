package com.theminespirit.core.intellect;

import java.util.Objects;
import java.util.Set;

/**
 * Какие занятия интеллекта — фоновые: задание доски (первые шаги, ремонт, еда, факелы) вытесняет их, а не ждёт конца
 * рубки четырёх деревьев или {@code wait 200}. Сон ночью не вытесняется: дух без сна ночью стоит под мобами.
 */
public final class BackgroundWork {
    private static final Set<String> PROTECTED = Set.of("sleep");

    private BackgroundWork() {}

    public static boolean preemptible(String script) {
        Objects.requireNonNull(script, "Скрипт занятия не может быть null: вытесняемость решается по имени");
        return !PROTECTED.contains(script);
    }
}

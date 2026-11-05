package com.theminespirit.core.reload;

import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class TableSlot<T> {
    private final Supplier<T> fallback;
    private volatile @Nullable Supplier<T> source;
    private volatile @Nullable T fallbackValue;

    public TableSlot(Supplier<T> fallback) {
        this.fallback = Objects.requireNonNull(
                fallback, "Запасной источник таблицы не может быть null: без него читатель не увидит значение");
    }

    public T current() {
        Supplier<T> chosen = source;
        if (chosen != null) {
            return chosen.get();
        }
        T value = fallbackValue;
        if (value == null) {
            value = fallback.get();
            fallbackValue = Objects.requireNonNull(
                    value, "Встроенная таблица не может быть null: пустую задают пустым значением, а не null");
        }
        return value;
    }

    public void use(Supplier<T> supplier) {
        source = Objects.requireNonNull(
                supplier, "Источник таблицы не может быть null: иначе подмена молча не сработает");
    }
}

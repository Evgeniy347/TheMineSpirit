package com.theminespirit.core.board;

import com.theminespirit.core.reload.TableHolder;
import java.util.Objects;
import java.util.function.Supplier;

public final class ConfigStamp {
    private static volatile Supplier<String> current = () -> "unknown";

    private ConfigStamp() {}

    public static String current() {
        return current.get();
    }

    public static String of(TableHolder<?> holder) {
        Objects.requireNonNull(holder, "Держатель таблицы не может быть null: без него неизвестен хэш");
        return holder.status().hash();
    }

    public static void useCurrent(Supplier<String> supplier) {
        current = Objects.requireNonNull(
                supplier, "Источник хэша конфигурации не может быть null: без него журнал теряет версию конфигурации");
    }
}

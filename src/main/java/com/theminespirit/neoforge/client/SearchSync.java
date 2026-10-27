package com.theminespirit.neoforge.client;

import java.util.Objects;
import java.util.function.Consumer;

public final class SearchSync {
    private static volatile Consumer<String> sink = text -> {};

    private SearchSync() {}

    public static void attach(Consumer<String> target) {
        sink = Objects.requireNonNull(target, "Приёмник поиска не может быть null: отключение — detach()");
    }

    public static void detach() {
        sink = text -> {};
    }

    static void publish(String text) {
        sink.accept(text);
    }
}

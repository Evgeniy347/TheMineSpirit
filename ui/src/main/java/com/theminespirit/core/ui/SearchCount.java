package com.theminespirit.core.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record SearchCount(int found, int loaded) {
    public SearchCount {
        if (found < 0 || loaded < 0 || found > loaded) {
            throw new IllegalArgumentException(
                    "Найдено " + found + " из " + loaded + ": найдённое не может превышать загруженное");
        }
    }

    public static SearchCount of(List<BoardTile> loaded, String query, Function<BoardTile, String> haystack) {
        Objects.requireNonNull(loaded, "Загруженные плитки не могут быть null: счётчик считает по ним");
        Objects.requireNonNull(query, "Запрос не может быть null: пустой запрос находят все плитки");
        Objects.requireNonNull(haystack, "Текст плитки не может быть null: без него не сравнить запрос");
        int found = 0;
        for (BoardTile tile : loaded) {
            if (BoardSearch.matches(haystack.apply(tile), query)) {
                found++;
            }
        }
        return new SearchCount(found, loaded.size());
    }

    public boolean empty() {
        return found == 0;
    }
}

package com.theminespirit.core.ui;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record BoardPageQuery(
        BoardTileGroup group,
        int offset,
        int limit,
        @Nullable BoardTileGroup status,
        BoardFilter.Spirit spirit,
        @Nullable String spiritName) {
    public static final int MAX_LIMIT = 512;

    public BoardPageQuery {
        Objects.requireNonNull(group, "Колонка запроса не может быть null: порцию ленты собирают по одной колонке");
        Objects.requireNonNull(spirit, "Режим исполнителя не может быть null: «все» задают Spirit.ANY");
        if (offset < 0) {
            throw new IllegalArgumentException(
                    "offset = " + offset + " отрицателен: лента начинается с начала, а не до него");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit = " + limit + " вне границ: сервер отдаёт от 1 до " + MAX_LIMIT + " плиток за порцию");
        }
        new BoardFilter(status, spirit, spiritName);
    }

    public BoardFilter filter() {
        return new BoardFilter(status, spirit, spiritName);
    }
}

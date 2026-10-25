package com.theminespirit.core.board;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ToolGate {
    private static final Map<BoardCase, String> REQUIRED =
            Map.of(BoardCase.CLEAR_GROUNDS, "_pickaxe", BoardCase.BUILD_MINE, "_pickaxe");

    private ToolGate() {}

    public static Optional<String> required(BoardCase boardCase) {
        Objects.requireNonNull(boardCase, "Кейс не может быть null: инструмент задания определяется по кейсу");
        return Optional.ofNullable(REQUIRED.get(boardCase));
    }

    public static boolean allows(BoardCase boardCase, Collection<String> items) {
        Objects.requireNonNull(items, "Инвентарь не может быть null: пустой — пустой набор");
        return required(boardCase)
                .map(suffix -> items.stream().anyMatch(item -> item.endsWith(suffix)))
                .orElse(true);
    }
}

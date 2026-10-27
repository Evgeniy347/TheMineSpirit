package com.theminespirit.core.board;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ToolGate {
    public static final String PICKAXE = "_pickaxe";

    public enum Hands {
        PICKAXE,
        BARE
    }

    public sealed interface Verdict permits Keep, Release {}

    public record Keep() implements Verdict {}

    public record Release(String reason) implements Verdict {
        public Release {
            Objects.requireNonNull(reason, "Причина снятия не может быть null: она пишется в журнал доски");
        }
    }

    private static final Map<BoardCase, Hands> TABLE = build();

    private ToolGate() {}

    private static Map<BoardCase, Hands> build() {
        Map<BoardCase, Hands> table = new EnumMap<>(BoardCase.class);
        table.put(BoardCase.CLEAR_GROUNDS, Hands.PICKAXE);
        table.put(BoardCase.BUILD_MINE, Hands.PICKAXE);
        table.put(BoardCase.GATHER_MATERIALS, Hands.BARE);
        table.put(BoardCase.CRAFTING_STATION, Hands.BARE);
        table.put(BoardCase.CRAFT_WOODEN_PICKAXE, Hands.BARE);
        table.put(BoardCase.CRAFT_WOODEN_AXE, Hands.BARE);
        table.put(BoardCase.CRAFT_WOODEN_SHOVEL, Hands.BARE);
        table.put(BoardCase.REPAIR_TOOL, Hands.BARE);
        table.put(BoardCase.ACTIVITY_CHOP, Hands.BARE);
        table.put(BoardCase.ACTIVITY_ESCAPE, Hands.BARE);
        return Map.copyOf(table);
    }

    public static Map<BoardCase, Hands> table() {
        return TABLE;
    }

    public static Optional<String> required(BoardCase boardCase) {
        Objects.requireNonNull(boardCase, "Кейс не может быть null: инструмент задания определяется по кейсу");
        return TABLE.get(boardCase) == Hands.PICKAXE ? Optional.of(PICKAXE) : Optional.empty();
    }

    public static boolean allows(BoardCase boardCase, Collection<String> items) {
        Objects.requireNonNull(items, "Инвентарь не может быть null: пустой — пустой набор");
        return required(boardCase)
                .map(suffix -> items.stream().anyMatch(item -> item.endsWith(suffix)))
                .orElse(true);
    }

    public static Verdict keep(BoardCase boardCase, Collection<String> items, boolean walledIn) {
        if (allows(boardCase, items)) {
            return new Keep();
        }
        if (walledIn) {
            return new Release("дух замурован и кирки нет: задание " + boardCase
                    + " возвращено доске, дух выбирается руками, кирка — первым делом после выхода");
        }
        return new Release(
                "кирки нет: задание " + boardCase
                        + " возвращено доске и ждёт кирку, руками камень не копаем — сначала добыть, сделать или починить кирку");
    }
}

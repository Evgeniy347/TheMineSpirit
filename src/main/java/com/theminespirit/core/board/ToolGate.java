package com.theminespirit.core.board;

import com.theminespirit.core.forestry.TreeSpecies;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class ToolGate {
    public static final String PICKAXE = "_pickaxe";
    public static final String AXE = "_axe";
    private static final String MINECRAFT = "minecraft:";

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
        table.put(BoardCase.MINE_LEFT_ORE, Hands.PICKAXE);
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

    public static Optional<String> required(BoardCase boardCase, @Nullable String supplyItem) {
        if (boardCase == BoardCase.GATHER_MATERIALS && isLog(supplyItem)) {
            return Optional.of(AXE);
        }
        return required(boardCase);
    }

    public static boolean allows(BoardCase boardCase, Collection<String> items) {
        return allows(boardCase, items, null);
    }

    public static boolean allows(BoardCase boardCase, Collection<String> items, @Nullable String supplyItem) {
        Objects.requireNonNull(items, "Инвентарь не может быть null: пустой — пустой набор");
        return required(boardCase, supplyItem)
                .map(suffix -> items.stream().anyMatch(item -> item.endsWith(suffix)))
                .orElse(true);
    }

    private static boolean isLog(@Nullable String item) {
        if (item == null) {
            return false;
        }
        String id = item.startsWith(MINECRAFT) ? item : MINECRAFT + item;
        return TreeSpecies.ofLog(id).isPresent();
    }

    public static Verdict keep(BoardCase boardCase, Collection<String> items, boolean walledIn) {
        return keep(boardCase, items, walledIn, null);
    }

    public static Verdict keep(
            BoardCase boardCase, Collection<String> items, boolean walledIn, @Nullable String supplyItem) {
        if (allows(boardCase, items, supplyItem)) {
            return new Keep();
        }
        String tool = required(boardCase, supplyItem).orElse("?");
        if (walledIn) {
            return new Release("дух замурован и " + tool + " нет: задание " + boardCase
                    + " возвращено доске, дух выбирается руками, инструмент — первым делом после выхода");
        }
        return new Release(tool + " нет: задание " + boardCase
                + " возвращено доске и ждёт инструмент, руками не работаем — сначала добыть, сделать или починить");
    }
}

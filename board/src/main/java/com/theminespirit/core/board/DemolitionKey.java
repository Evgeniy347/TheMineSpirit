package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public final class DemolitionKey {
    public static final String PREFIX = "demolish:";

    private DemolitionKey() {}

    public static String idFor(List<BlockCoord> piece) {
        Objects.requireNonNull(piece, "Кусок уборки не может быть null: без него нет билета");
        if (piece.isEmpty()) {
            throw new IllegalArgumentException("Кусок уборки пуст: уборке нечего сносить");
        }
        return idFor(piece.get(0));
    }

    public static String idFor(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Начало куска не может быть null: по нему держится id билета");
        long hash = 17L * 31L + anchor.hashCode();
        return PREFIX + Long.toUnsignedString(hash, 36);
    }

    public static List<BlockCoord> piece(List<BlockCoord> candidates) {
        Objects.requireNonNull(candidates, "Кандидаты уборки не могут быть null: без них нет куска");
        if (candidates.isEmpty()) {
            return List.of();
        }
        BlockCoord anchor = candidates.get(0);
        return candidates.stream()
                .filter(at -> Math.abs(at.x() - anchor.x())
                                <= BoardConfig.current().plans().demolitionRadius()
                        && Math.abs(at.z() - anchor.z())
                                <= BoardConfig.current().plans().demolitionRadius()
                        && at.y() >= anchor.y()
                        && at.y() <= anchor.y() + BoardConfig.current().plans().demolitionHeight())
                .limit(BoardConfig.current().plans().demolitionBatch())
                .toList();
    }

    public static boolean withinStand(BlockCoord stand, List<BlockCoord> cells) {
        Objects.requireNonNull(stand, "Стенд уборки не может быть null: вокруг него собирают пакет");
        Objects.requireNonNull(cells, "Клетки дерева не могут быть null: пустое дерево не собирают");
        return cells.stream()
                .allMatch(at -> Math.abs(at.x() - stand.x())
                                <= BoardConfig.current().plans().demolitionRadius()
                        && Math.abs(at.z() - stand.z())
                                <= BoardConfig.current().plans().demolitionRadius());
    }

    public static List<BlockCoord> treePiece(List<BlockCoord> candidates, BlockCoord stand) {
        Objects.requireNonNull(candidates, "Кандидаты уборки не могут быть null: без них нет куска");
        Objects.requireNonNull(stand, "Стенд уборки не может быть null: вокруг него собирают пакет");
        return candidates.stream()
                .filter(at -> Math.abs(at.x() - stand.x())
                                <= BoardConfig.current().plans().demolitionRadius()
                        && Math.abs(at.z() - stand.z())
                                <= BoardConfig.current().plans().demolitionRadius())
                .limit(BoardConfig.current().plans().demolitionBatch())
                .toList();
    }
}

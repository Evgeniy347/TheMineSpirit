package com.theminespirit.core.policy;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.OptionalInt;

public final class DropPolicy {
    public static final int MAX_DROP = 2;

    private DropPolicy() {
    }

    public static OptionalInt fallBelow(BlockCoord from, SafetyView view) {
        Objects.requireNonNull(from, "Клетка падения не может быть null: от неё считается глубина");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему читаются клетки под духом");
        for (int depth = 0; depth <= MAX_DROP; depth++) {
            Cell below = view.cellAt(new BlockCoord(from.x(), from.y() - depth - 1, from.z()));
            if (below == Cell.SOLID) {
                return OptionalInt.of(depth);
            }
            if (below != Cell.PASSABLE) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }
}

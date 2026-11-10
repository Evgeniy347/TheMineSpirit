package com.theminespirit.core.board.lease;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record LeaseCell(
        BlockCoord pos,
        LeaseKind kind,
        @Nullable String before,
        @Nullable String after) {
    public LeaseCell {
        Objects.requireNonNull(pos, "Клетка аренды не может быть null: откатывать нечего");
        Objects.requireNonNull(kind, "Вид правки клетки не может быть null: от него зависит откат");
        if (kind == LeaseKind.BROKEN && blank(before)) {
            throw new IllegalArgumentException("Сломанная клетка " + pos + " не помнит прежний блок: "
                    + "вернуть её после брошенной аренды будет нечем");
        }
        if (kind == LeaseKind.STATE && (blank(before) || blank(after))) {
            throw new IllegalArgumentException("Клетка состояния " + pos + " не помнит состояние до и после: "
                    + "закрыть дверь или снять рычаг будет нечем");
        }
    }

    public static LeaseCell placed(BlockCoord pos) {
        return new LeaseCell(pos, LeaseKind.PLACED, null, null);
    }

    public static LeaseCell placed(BlockCoord pos, String block) {
        return new LeaseCell(pos, LeaseKind.PLACED, null, block);
    }

    public static LeaseCell broken(BlockCoord pos, String before) {
        return new LeaseCell(pos, LeaseKind.BROKEN, before, null);
    }

    public static LeaseCell state(BlockCoord pos, String before, String after) {
        return new LeaseCell(pos, LeaseKind.STATE, before, after);
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}

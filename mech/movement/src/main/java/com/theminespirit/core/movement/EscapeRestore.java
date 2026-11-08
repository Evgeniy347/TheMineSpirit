package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public final class EscapeRestore {
    private EscapeRestore() {}

    public static boolean blockedByBody(BlockCoord cell, BlockCoord feet) {
        Objects.requireNonNull(cell, "Клетка возврата не может быть null: неизвестно, что закладывать");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них не видно, где он стоит");
        return cell.equals(feet) || (cell.x() == feet.x() && cell.z() == feet.z() && cell.y() == feet.y() + 1);
    }
}
